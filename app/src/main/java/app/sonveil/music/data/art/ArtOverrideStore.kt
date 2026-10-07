package app.sonveil.music.data.art

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Client-only cover/artist image overrides.
 *
 * Layout: `filesDir/art_overrides/{album|artist}/<sha256Id>.jpg`
 *
 * - Never writes embedded tags or library files on the server
 * - Never calls Subsonic upload / invented setCoverArt APIs
 * - Does not touch PlayerSettings (sleep_timer / wifi_only_hires_dl keys)
 */
class ArtOverrideStore internal constructor(
    root: File,
    private val openInputStream: (Uri) -> InputStream? = { error("No content resolver") },
) {
    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, "art_overrides"),
        { uri -> context.applicationContext.contentResolver.openInputStream(uri) },
    )

    private val mutableRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val revisions = mutableRevisions.asStateFlow()
    // Avoid reusing Coil entries left by an earlier store/process instance.
    val cacheNamespace: String = UUID.randomUUID().toString()

    fun albumRevisionKey(id: String): String = "album/${sanitizeId(id)}"

    private fun changed(dir: File, id: String) {
        val key = "${dir.name}/${sanitizeId(id)}"
        val old = mutableRevisions.value
        mutableRevisions.value = old + (key to ((old[key] ?: 0L) + 1L))
    }
    private val albumDir = File(root, "album").apply { mkdirs() }
    private val artistDir = File(root, "artist").apply { mkdirs() }

    fun getAlbumOverrideUri(albumId: String): Uri? = uriIfExists(albumDir, albumId)
    fun getArtistOverrideUri(artistId: String): Uri? = uriIfExists(artistDir, artistId)

    fun hasAlbumOverride(albumId: String): Boolean = fileFor(albumDir, albumId).isFile
    fun hasArtistOverride(artistId: String): Boolean = fileFor(artistDir, artistId).isFile

    fun setAlbumOverride(albumId: String, bytes: ByteArray) {
        writeBytes(albumDir, albumId, bytes)
    }

    fun setArtistOverride(artistId: String, bytes: ByteArray) {
        writeBytes(artistDir, artistId, bytes)
    }

    fun setAlbumOverride(albumId: String, uri: Uri) {
        writeUri(albumDir, albumId, uri)
    }

    fun setArtistOverride(artistId: String, uri: Uri) {
        writeUri(artistDir, artistId, uri)
    }

    fun clearAlbumOverride(albumId: String) {
        clear(albumDir, albumId)
    }

    fun clearArtistOverride(artistId: String) {
        clear(artistDir, artistId)
    }

    private fun uriIfExists(dir: File, id: String): Uri? {
        val f = fileFor(dir, id)
        return if (f.isFile && f.length() > 0L) Uri.fromFile(f) else null
    }

    @Synchronized
    private fun clear(dir: File, id: String) {
        val target = fileFor(dir, id)
        check(!target.exists() || target.delete()) { "Cannot delete artwork" }
        changed(dir, id)
    }

    @Synchronized
    private fun writeBytes(dir: File, id: String, bytes: ByteArray) {
        require(bytes.isNotEmpty()) { "empty image bytes" }
        require(bytes.size <= MAX_IMAGE_BYTES) { "Artwork is too large (maximum 8 MiB)" }
        val target = fileFor(dir, id)
        val tmp = File.createTempFile("override-", ".tmp", dir)
        try {
            tmp.writeBytes(bytes)
            check(tmp.renameTo(target)) { "Cannot replace artwork" }
            changed(dir, id)
        } finally {
            tmp.delete()
        }
    }

    private fun writeUri(dir: File, id: String, uri: Uri) {
        openInputStream(uri).use { input ->
            requireNotNull(input) { "cannot open image" }
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= MAX_IMAGE_BYTES) { "Artwork is too large (maximum 8 MiB)" }
                output.write(buffer, 0, count)
            }
            writeBytes(dir, id, output.toByteArray())
        }
    }

    private fun fileFor(dir: File, id: String): File =
        File(dir, sanitizeId(id) + ".jpg")

    companion object {
        internal const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        fun sanitizeId(id: String): String =
            MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    /** Brief API: album or artist override Uri, or null. */
    fun getOverrideUri(albumId: String? = null, artistId: String? = null): Uri? = when {
        !albumId.isNullOrBlank() -> getAlbumOverrideUri(albumId)
        !artistId.isNullOrBlank() -> getArtistOverrideUri(artistId)
        else -> null
    }

    fun setOverride(albumId: String? = null, artistId: String? = null, bytes: ByteArray) {
        when {
            !albumId.isNullOrBlank() -> setAlbumOverride(albumId, bytes)
            !artistId.isNullOrBlank() -> setArtistOverride(artistId, bytes)
            else -> error("albumId or artistId required")
        }
    }

    fun setOverride(albumId: String? = null, artistId: String? = null, uri: Uri) {
        when {
            !albumId.isNullOrBlank() -> setAlbumOverride(albumId, uri)
            !artistId.isNullOrBlank() -> setArtistOverride(artistId, uri)
            else -> error("albumId or artistId required")
        }
    }

    fun clearOverride(albumId: String? = null, artistId: String? = null) {
        when {
            !albumId.isNullOrBlank() -> clearAlbumOverride(albumId)
            !artistId.isNullOrBlank() -> clearArtistOverride(artistId)
            else -> error("albumId or artistId required")
        }
    }

}
