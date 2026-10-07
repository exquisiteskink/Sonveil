package app.sonveil.music.data.download

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call

/** Joins blocking IO before returning, including on cancellation. Never publishes partial files. */
internal object OfflineTransfer {
    suspend fun download(call: Call, target: File, expectedSize: Long, storageBudget: Long = Long.MAX_VALUE, commit: (File) -> Unit) =
        withContext(Dispatchers.IO) {
            coroutineScope {
                // Unconfined so cancellation closes a blocked socket immediately, not after read returns.
                val cancellation = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { call.cancel() }
                }
                val tmp = File.createTempFile("download-", ".part", target.parentFile)
                var published = false
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
                        val body = response.body ?: throw IOException("Empty download")
                        if (body.contentLength() > storageBudget) throw IOException("Offline storage limit reached (32 GiB)")
                        val type = body.contentType()?.let { "${it.type}/${it.subtype}" }.orEmpty().lowercase()
                        if (type.isNotEmpty() && !type.startsWith("audio/") && type !in setOf(
                                "application/octet-stream", "binary/octet-stream", "application/ogg", "video/mp4")) {
                            throw IOException("Server returned a non-audio response")
                        }
                        val source = body.source()
                        source.request(512)
                        val prefix = source.buffer.clone().readByteArray(minOf(512L, source.buffer.size))
                        val text = prefix.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                        if (text.startsWith("<") || text.startsWith("{") || text.startsWith("[")) {
                            throw IOException("Server returned an error document instead of audio")
                        }
                        if (!hasAudioHeader(prefix)) throw IOException("Unrecognized audio file")
                        tmp.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            val input = body.byteStream()
                            var bytes = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count == -1) break
                                bytes += count
                                if (bytes > storageBudget) throw IOException("Offline storage limit reached (32 GiB)")
                                out.write(buffer, 0, count)
                            }
                        }
                        val actual = tmp.length()
                        val contentLen = body.contentLength()
                        // Prefer HTTP Content-Length. Subsonic metadata `size` is often
                        // wrong vs the original download body; requiring both rejects valid files.
                        val incomplete = when {
                            actual == 0L -> true
                            contentLen >= 0L -> actual != contentLen
                            expectedSize > 0L -> actual != expectedSize
                            else -> false
                        }
                        if (incomplete) {
                            throw IOException("Incomplete download; retry this track")
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    check(tmp.renameTo(target)) { "Cannot save download" }
                    published = true
                    currentCoroutineContext().ensureActive()
                    commit(target)
                    published = false
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    throw e
                } finally {
                    if (published) target.delete()
                    tmp.delete()
                    cancellation.cancel()
                }
            }
        }

    private fun hasAudioHeader(bytes: ByteArray): Boolean {
        val head = bytes.toString(Charsets.ISO_8859_1)
        return head.startsWith("fLaC") || head.startsWith("ID3") || head.startsWith("OggS") ||
            head.startsWith("RIFF") || head.startsWith("RF64") || head.startsWith("FORM") ||
            head.startsWith("MAC ") || head.startsWith("wvpk") || head.startsWith("DSD ") ||
            head.startsWith("FRM8") || (head.length >= 8 && head.substring(4, 8) == "ftyp") ||
            (bytes.size >= 2 && bytes[0].toInt() and 0xff == 0xff && bytes[1].toInt() and 0xe0 == 0xe0)
    }
}
