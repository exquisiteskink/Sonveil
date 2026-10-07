package app.sonveil.music.data.download

import android.content.Context
import android.net.Uri
import app.sonveil.music.data.auth.StoredCredentials
import app.sonveil.music.data.remote.Song
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json

/**
 * App-private offline media store. Persists only relative paths and metadata —
 * never authenticated download/stream URLs (credentials live in query strings).
 */
class DownloadStore internal constructor(private val root: File) {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, "offline"))
    init { root.mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedIndex: OfflineIndex? = null

    fun serverKey(creds: StoredCredentials): String {
        val identity = if (creds.authMode == app.sonveil.music.data.auth.AuthMode.ApiKey) creds.apiKey else creds.username
        val parts = listOf(creds.serverUrl.trim().trimEnd('/'), if (creds.authMode == app.sonveil.music.data.auth.AuthMode.ApiKey) "api" else "user", identity)
        return sanitizeId(parts.joinToString("") { "${it.length}:$it" })
    }

    private fun serverDir(key: String): File =
        File(root, key).apply { mkdirs() }

    private fun songsDir(key: String): File =
        File(serverDir(key), "songs").apply { mkdirs() }

    private fun indexFile(key: String): File =
        File(serverDir(key), "index.json")

    @Synchronized
    fun loadIndex(key: String): OfflineIndex {
        if (cachedKey == key && cachedIndex != null) return cachedIndex!!
        val file = indexFile(key)
        val index = if (file.isFile) {
            runCatching { json.decodeFromString(OfflineIndex.serializer(), file.readText()) }
                .getOrElse { OfflineIndex(serverKey = key) }
        } else {
            OfflineIndex(serverKey = key)
        }
        cachedKey = key
        cachedIndex = index
        return index
    }

    @Synchronized
    private fun saveIndex(index: OfflineIndex) {
        val file = indexFile(index.serverKey)
        val tmp = File(file.parentFile, "index.json.tmp")
        tmp.writeText(json.encodeToString(OfflineIndex.serializer(), index))
        check(tmp.renameTo(file)) { "Cannot save download index" }
        cachedKey = index.serverKey
        cachedIndex = index
    }

    fun hasSong(key: String, songId: String): Boolean {
        val rec = loadIndex(key).songs[songId] ?: return false
        return validFile(key, rec) != null
    }

    fun playbackUri(key: String, songId: String): Uri? {
        val rec = loadIndex(key).songs[songId] ?: return null
        return validFile(key, rec)?.let(Uri::fromFile)
    }

    private fun validFile(key: String, rec: OfflineSongRecord): File? {
        val dir = serverDir(key).canonicalFile
        val file = File(dir, rec.relPath).canonicalFile
        return file.takeIf {
            it.path.startsWith(dir.path + File.separator) && it.isFile && it.length() > 0 && it.length() == rec.size
        }
    }

    @Synchronized
    fun availableSongs(key: String): List<Song> = loadIndex(key).songs.values
        .filter { validFile(key, it) != null }
        .map { rec ->
            rec.song ?: Song(id = rec.songId, title = rec.title, artist = rec.artist, album = rec.album,
                albumId = rec.albumId, suffix = rec.suffix, contentType = rec.contentType, bitRate = rec.bitRate)
        }
        .sortedWith(compareBy({ it.album.orEmpty() }, { it.discNumber }, { it.track }, { it.title }))

    /** Absolute target file for a song (may not exist yet). */
    fun targetFile(key: String, song: Song): File {
        val suffix = song.suffix?.trim('.')?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) } ?: guessSuffix(song.contentType) ?: "bin"
        val safeId = sanitizeId(song.id)
        return File(songsDir(key), "$safeId.$suffix")
    }

    @Synchronized
    fun markDownloaded(key: String, song: Song, file: File) {
        val rel = file.relativeTo(serverDir(key)).path.replace('\\', '/')
        val rec = OfflineSongRecord(
            songId = song.id,
            relPath = rel,
            size = file.length(),
            suffix = song.suffix,
            contentType = song.contentType,
            bitRate = song.bitRate,
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumId = song.albumId,
            downloadedAtMs = System.currentTimeMillis(),
            song = song,
        )
        val index = loadIndex(key)
        saveIndex(index.copy(songs = index.songs + (song.id to rec)))
    }

    @Synchronized
    fun removeSong(key: String, songId: String) {
        val index = loadIndex(key)
        val rec = index.songs[songId]
        if (rec != null) {
            File(serverDir(key), rec.relPath).delete()
            saveIndex(index.copy(songs = index.songs - songId))
        }
    }

    private fun pendingFile(key: String): File = File(serverDir(key), "pending.json")

    @Synchronized
    fun savePending(key: String, pending: PendingDownload) {
        val file = pendingFile(key)
        val tmp = File(file.parentFile, "pending.json.tmp")
        tmp.writeText(json.encodeToString(PendingDownload.serializer(), pending))
        check(tmp.renameTo(file)) { "Cannot save download queue" }
    }

    @Synchronized
    fun loadPending(key: String): PendingDownload? {
        val file = pendingFile(key)
        if (!file.isFile) return null
        return runCatching { json.decodeFromString(PendingDownload.serializer(), file.readText()) }
            .getOrNull()
            ?.takeIf { it.songs.isNotEmpty() }
    }

    @Synchronized
    fun clearPending(key: String) {
        pendingFile(key).delete()
    }

    fun totalBytesUsed(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun bytesUsed(key: String): Long {
        val dir = songsDir(key)
        if (!dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    @Synchronized
    fun clearAll(key: String) {
        check(serverDir(key).deleteRecursively()) { "Cannot clear downloads" }
        if (cachedKey == key) {
            cachedKey = null
            cachedIndex = null
        }
        serverDir(key).mkdirs()
        songsDir(key)
        saveIndex(OfflineIndex(serverKey = key))
    }

    @Synchronized
    fun clearEverything() {
        check(root.deleteRecursively()) { "Cannot clear downloads" }
        root.mkdirs()
        cachedKey = null
        cachedIndex = null
    }

    companion object {
        fun sanitizeId(id: String): String =
            MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun guessSuffix(contentType: String?): String? = when {
            contentType == null -> null
            contentType.contains("flac", true) -> "flac"
            contentType.contains("mpeg", true) || contentType.contains("mp3", true) -> "mp3"
            contentType.contains("mp4", true) || contentType.contains("aac", true) || contentType.contains("m4a", true) -> "m4a"
            contentType.contains("ogg", true) || contentType.contains("opus", true) -> "ogg"
            contentType.contains("wav", true) -> "wav"
            contentType.contains("aiff", true) -> "aiff"
            else -> null
        }
    }
}
