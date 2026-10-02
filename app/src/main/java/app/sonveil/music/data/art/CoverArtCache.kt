package app.sonveil.music.data.art

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Raw artwork cache for car clients. Authenticated URLs are hashed, never persisted. */
internal class CoverArtCache(
    private val directory: File,
    http: OkHttpClient,
    private val maxImageBytes: Long = 8L * 1024 * 1024,
    private val maxCacheBytes: Long = 64L * 1024 * 1024,
) {
    private val http = http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
    private val requestLocks = Array(16) { Any() }

    fun get(url: String): File = withFile(url) { it }

    // Deduplicate matching covers while allowing unrelated downloads in parallel.
    // Open descriptors under the publication/eviction lock so paths cannot vanish
    // between a cache lookup and the caller's open operation.
    fun <T> withFile(url: String, open: (File) -> T): T =
        synchronized(requestLocks[(url.hashCode() and Int.MAX_VALUE) % requestLocks.size]) request@ {
            check(directory.mkdirs() || directory.isDirectory) { "Cannot create artwork cache" }
            // Includes server, account, authentication and requested size. An id alone
            // is not unique across servers/accounts and can return someone else's art.
            val target = File(directory, "${ArtOverrideStore.sanitizeId(url)}.img")
            synchronized(this) {
                if (target.isFile && target.length() > 0L) {
                    target.setLastModified(System.currentTimeMillis())
                    return@request open(target)
                }
            }
            val tmp = File.createTempFile("cover-", ".tmp", directory)
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Artwork HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty artwork body")
                    if (body.contentLength() > maxImageBytes) throw IOException("Artwork is too large")
                    val type = body.contentType()
                    if (type != null && type.type != "image" &&
                        !(type.type == "application" && type.subtype == "octet-stream")) {
                        throw IOException("Response is not artwork")
                    }
                    var bytes = 0L
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytes += count
                                if (bytes > maxImageBytes) throw IOException("Artwork is too large")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (bytes == 0L) throw IOException("Empty artwork download")
                }
                synchronized(this) {
                    if (!tmp.renameTo(target)) throw IOException("Cannot publish artwork")
                    trimExcept(target)
                    open(target)
                }
            } finally {
                tmp.delete()
            }
        }

    private fun trimExcept(current: File) {
        val files = directory.listFiles()?.filter { it.isFile && it.extension == "img" }.orEmpty()
        var bytes = files.sumOf { it.length() }
        for (file in files.filter { it != current }.sortedBy { it.lastModified() }) {
            if (bytes <= maxCacheBytes) break
            val size = file.length()
            if (file.delete()) bytes -= size
        }
    }
}
