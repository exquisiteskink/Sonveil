package app.sonveil.music.data.art

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.sonveil.music.AuralisApp
import app.sonveil.music.data.remote.SubsonicClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Exposes Subsonic cover art as local `content://` URIs for Android Auto / AAOS.
 *
 * Car media requires [ContentResolver.SCHEME_CONTENT] or
 * [ContentResolver.SCHEME_ANDROID_RESOURCE] artwork URIs — not HTTP(S).
 * Browse returns these URIs immediately; [openFile] downloads/caches on demand.
 *
 * @see <a href="https://developer.android.com/training/cars/media/create-media-browser/media-artwork">Display media artwork</a>
 */
class CoverArtContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (!mode.contains('r') || mode.contains('w')) {
            throw FileNotFoundException("Cover art is read-only: $uri")
        }
        val parsed = parse(uri) ?: throw FileNotFoundException("Bad cover URI: $uri")
        val context = context ?: throw FileNotFoundException("No context")
        val app = context.applicationContext as? AuralisApp
            ?: throw FileNotFoundException("App not ready")
        val container = app.container

        // Prefer client-only album override when cover id matches an album override key.
        container.artOverrides.getAlbumOverrideUri(parsed.coverId)?.path?.let { path ->
            val override = File(path)
            if (override.isFile && override.length() > 0L) {
                return ParcelFileDescriptor.open(override, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }

        val cacheFile = cacheFile(context, parsed)
        if (!cacheFile.isFile || cacheFile.length() == 0L) {
            downloadToCache(container.client, parsed, cacheFile)
        }
        if (!cacheFile.isFile || cacheFile.length() == 0L) {
            throw FileNotFoundException("Cover not available: ${parsed.coverId}")
        }
        return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? =
        if (parse(uri) != null) "image/jpeg" else null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "app.sonveil.music.coverart"
        private const val PATH_COVER = "cover"
        private const val DOWNLOAD_TIMEOUT_SECONDS = 30L

        data class CoverRef(val coverId: String, val size: Int)

        /**
         * Build a content URI for [coverId] without downloading.
         * Returns null when [coverId] is blank.
         */
        fun contentUri(coverId: String?, size: Int = 400): Uri? {
            val path = buildEncodedPath(coverId, size) ?: return null
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .encodedPath(path)
                .build()
        }

        fun parse(uri: Uri): CoverRef? {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
            if (uri.authority != AUTHORITY) return null
            return parseEncodedPath(uri.encodedPath)
        }

        /** Pure helper for unit tests: `/cover/{size}/{urlEncodedCoverId}`. */
        fun buildEncodedPath(coverId: String?, size: Int): String? {
            if (coverId.isNullOrBlank()) return null
            val safeSize = size.coerceIn(32, 2048)
            val encodedId = URLEncoder.encode(coverId.trim(), StandardCharsets.UTF_8.name())
                .replace("+", "%20")
            return "/$PATH_COVER/$safeSize/$encodedId"
        }

        fun parseEncodedPath(encodedPath: String?): CoverRef? {
            if (encodedPath.isNullOrBlank()) return null
            val parts = encodedPath.trim('/').split('/', limit = 3)
            if (parts.size != 3 || parts[0] != PATH_COVER) return null
            val size = parts[1].toIntOrNull()?.coerceIn(32, 2048) ?: return null
            val coverId = try {
                URLDecoder.decode(parts[2], StandardCharsets.UTF_8.name()).trim()
            } catch (_: Exception) {
                return null
            }
            if (coverId.isEmpty() || coverId.contains("..")) return null
            return CoverRef(coverId, size)
        }

        private fun cacheFile(context: Context, ref: CoverRef): File {
            val dir = File(context.cacheDir, "coverart").apply { mkdirs() }
            val name = "${ref.size}_${ArtOverrideStore.sanitizeId(ref.coverId)}.jpg"
            return File(dir, name)
        }

        @Throws(FileNotFoundException::class)
        private fun downloadToCache(client: SubsonicClient, ref: CoverRef, target: File) {
            val url = client.coverUrl(ref.coverId, ref.size)
                ?: throw FileNotFoundException("Not signed in or missing cover id")
            val parent = target.parentFile ?: throw FileNotFoundException("No cache dir")
            parent.mkdirs()
            val tmp = File.createTempFile("cover-", ".tmp", parent)
            try {
                val request = Request.Builder().url(url).get().build()
                val http = client.http.newBuilder()
                    .callTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw FileNotFoundException("HTTP ${response.code} for cover ${ref.coverId}")
                    }
                    val body = response.body ?: throw FileNotFoundException("Empty cover body")
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                if (tmp.length() == 0L) throw FileNotFoundException("Empty cover download")
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            } catch (e: FileNotFoundException) {
                tmp.delete()
                throw e
            } catch (e: IOException) {
                tmp.delete()
                throw FileNotFoundException("Cover download failed: ${e.message}")
            } catch (e: Exception) {
                tmp.delete()
                throw FileNotFoundException("Cover download failed: ${e.message}")
            }
        }
    }
}
