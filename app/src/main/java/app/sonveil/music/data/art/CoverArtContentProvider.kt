package app.sonveil.music.data.art

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.sonveil.music.SonveilApp
import app.sonveil.music.data.auth.StoredCredentials
import java.io.File
import java.io.FileNotFoundException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking

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

    private val cache by lazy {
        val context = requireNotNull(context)
        val app = context.applicationContext as SonveilApp
        // Use a new directory so old id-only entries are never reused.
        CoverArtCache(File(context.cacheDir, "coverart-v2"), app.container.client.http)
    }

    private val albumCoverIds = AlbumCoverIds()

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (!mode.contains('r') || mode.contains('w')) {
            throw FileNotFoundException("Cover art is read-only: $uri")
        }
        val parsed = parse(uri) ?: throw FileNotFoundException("Bad cover URI: $uri")
        val context = context ?: throw FileNotFoundException("No context")
        val app = context.applicationContext as? SonveilApp
            ?: throw FileNotFoundException("App not ready")
        val container = app.container
        val credentials = container.client.credentials ?: throw FileNotFoundException("Not signed in")
        val namespace = container.client.artworkNamespace
        val account = accountScope(credentials, namespace)
        if (parsed.accountScope != account || !ArtworkAccess.permits(
                if (parsed.isAlbum) "$namespace:album" else namespace, account, parsed.coverId, parsed.size, uri.getQueryParameter("access"),
            )) {
            throw FileNotFoundException("Artwork access denied")
        }

        return try {
            val coverId = if (parsed.isAlbum) {
                albumCoverIds.resolve(account, parsed.coverId) {
                    // openFile runs on the image loader / content provider worker,
                    // never the playback thread. Fetch the exact album screen cover.
                    val cover = runBlocking { container.client.getAlbum(parsed.coverId).coverArt }
                    if (container.client.artworkNamespace != namespace) {
                        throw FileNotFoundException("Artwork account changed")
                    }
                    cover
                }
            } else parsed.coverId
            val url = container.client.coverUrl(coverId, parsed.size, credentials)
                ?: throw FileNotFoundException("Album cover not available")
            cache.withFile(url) { file ->
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        } catch (e: Exception) {
            throw FileNotFoundException("Cover not available").apply { initCause(e) }
        }
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
        private const val PATH_ALBUM = "album"

        data class CoverRef(val coverId: String, val size: Int, val accountScope: String? = null, val isAlbum: Boolean = false)

        /** Separate host-side artwork caches without exposing credentials in content URIs. */
        fun accountScope(credentials: StoredCredentials, namespace: String): String = ArtOverrideStore.sanitizeId(
            listOf(namespace, credentials.serverUrl.trim().trimEnd('/'), credentials.username,
                credentials.authMode.name, credentials.apiKey).joinToString("") { "${it.length}:$it" },
        )

        /**
         * Build a content URI for [coverId] without downloading.
         * Returns null when [coverId] is blank.
         */
        fun contentUri(coverId: String?, size: Int = 400, accountScope: String? = null, accessToken: String? = null, isAlbum: Boolean = false): Uri? {
            val path = buildEncodedPath(coverId, size, isAlbum) ?: return null
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .encodedPath(path)
                .apply {
                    if (accountScope != null) appendQueryParameter("account", accountScope)
                    if (accessToken != null) appendQueryParameter("access", accessToken)
                }
                .build()
        }

        /** Issued capabilities work with Auto's content-URI loader without broad caller permissions.
         * URLs expire when the session namespace rotates on login/logout or process restart.
         */
        fun authorizedContentUri(coverId: String?, size: Int, credentials: StoredCredentials?, namespace: String): Uri? {
            if (coverId.isNullOrBlank() || credentials == null) return null
            val account = accountScope(credentials, namespace)
            return contentUri(coverId, size, account, ArtworkAccess.token(namespace, account, coverId, size))
        }

        /** Resolve the server album cover lazily, shared by phone, notification and Auto. */
        fun authorizedAlbumContentUri(albumId: String?, size: Int, credentials: StoredCredentials?, namespace: String): Uri? {
            if (albumId.isNullOrBlank() || credentials == null) return null
            val account = accountScope(credentials, namespace)
            return contentUri(albumId, size, account, ArtworkAccess.token("$namespace:album", account, albumId, size), isAlbum = true)
        }

        fun parse(uri: Uri): CoverRef? {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
            if (uri.authority != AUTHORITY) return null
            return parseEncodedPath(uri.encodedPath)?.copy(accountScope = uri.getQueryParameter("account"))
        }

        /** Pure helper for unit tests: `/cover/{size}/{urlEncodedCoverId}`. */
        fun buildEncodedPath(coverId: String?, size: Int, isAlbum: Boolean = false): String? {
            if (coverId.isNullOrBlank()) return null
            val safeSize = size.coerceIn(32, 2048)
            val encodedId = URLEncoder.encode(coverId.trim(), StandardCharsets.UTF_8.name())
                .replace("+", "%20")
            val kind = if (isAlbum) PATH_ALBUM else PATH_COVER
            return "/$kind/$safeSize/$encodedId"
        }

        fun parseEncodedPath(encodedPath: String?): CoverRef? {
            if (encodedPath.isNullOrBlank()) return null
            val parts = encodedPath.trim('/').split('/', limit = 3)
            if (parts.size != 3 || parts[0] !in listOf(PATH_COVER, PATH_ALBUM)) return null
            val size = parts[1].toIntOrNull()?.coerceIn(32, 2048) ?: return null
            val coverId = try {
                URLDecoder.decode(parts[2], StandardCharsets.UTF_8.name()).trim()
            } catch (_: Exception) {
                return null
            }
            if (coverId.isEmpty() || coverId.contains("..")) return null
            return CoverRef(coverId, size, isAlbum = parts[0] == PATH_ALBUM)
        }
    }
}
