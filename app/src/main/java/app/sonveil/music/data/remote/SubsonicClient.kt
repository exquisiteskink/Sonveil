package app.sonveil.music.data.remote

import app.sonveil.music.data.auth.AuthMode
import app.sonveil.music.data.auth.StoredCredentials
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.random.asKotlinRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class SubsonicException(val code: Int, message: String) : Exception(message)

data class ServerInfo(
    val type: String?,
    val version: String,
    val serverVersion: String?,
    val openSubsonic: Boolean,
    val extensions: List<String>,
)

class SubsonicClient(
    val http: OkHttpClient = buildHttpClient(),
) {
    @Volatile
    var credentials: StoredCredentials? = null

    /** Private random namespace prevents artwork URIs from revealing credential hashes. */
    @Volatile
    internal var artworkNamespace: String = randomSalt(32)
        private set

    /**
     * Stable salt for cover-art URLs so Coil can cache within a session.
     * Stream/download URLs intentionally use a fresh salt per [streamUrl]/[downloadUrl]
     * build ([buildUrl] session=false) so MediaItem rebuilds are not identical
     * enqueue-time strings (help after keep-alive drops / sticky player errors).
     */
    @Volatile
    private var sessionSalt: String = randomSalt()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val baseUrl: String
        get() = credentials?.serverUrl?.trim()?.trimEnd('/') ?: ""

    fun rotateSessionSalt() {
        sessionSalt = randomSalt()
        artworkNamespace = randomSalt(32)
    }

    suspend fun ping(): ServerInfo {
        return ping(credentials ?: throw SubsonicException(40, "Not signed in"))
    }

    private suspend fun ping(candidate: StoredCredentials): ServerInfo {
        val env = request("ping", emptyMap(), candidate)
        return ServerInfo(
            type = env.type,
            version = env.version,
            serverVersion = env.serverVersion,
            openSubsonic = env.openSubsonic,
            extensions = env.openSubsonicExtensions.map { it.name },
        )
    }

    suspend fun login(candidate: StoredCredentials): Pair<StoredCredentials, ServerInfo> {
        val url = runCatching { candidate.serverUrl.trim().trimEnd('/').toHttpUrl() }
            .getOrElse { throw SubsonicException(0, "Invalid server URL") }
        requireAllowedAuthenticatedUrl(url, candidate)
        val result = try {
            val info = ping(candidate)
            candidate to info
        } catch (e: SubsonicException) {
            if (e.code == 41 && candidate.authMode == AuthMode.Token && candidate.password.isNotEmpty()) {
                if (url.scheme != "https") {
                    throw SubsonicException(
                        41,
                        "This server needs password auth, which Sonveil only sends over HTTPS. Use HTTPS or an API key.",
                    )
                }
                val fallback = candidate.copy(authMode = AuthMode.HexPassword)
                fallback to ping(fallback)
            } else {
                throw e
            }
        }
        // Publish credentials only after authentication succeeds (including fallback).
        credentials = result.first
        rotateSessionSalt()
        return result
    }

    suspend fun getPlaylists(): List<Playlist> =
        get("getPlaylists").playlists?.playlist.orEmpty()

    suspend fun getPlaylist(id: String): PlaylistWithSongs =
        get("getPlaylist", "id" to id).playlist
            ?: throw SubsonicException(70, "Playlist not found")

    suspend fun getMusicFolders(): List<MusicFolder> =
        get("getMusicFolders").musicFolders?.musicFolder.orEmpty()

    suspend fun getAlbumList2(
        type: String,
        size: Int = 24,
        offset: Int = 0,
        musicFolderId: String? = null,
    ): List<AlbumID3> {
        val params = mutableListOf(
            "type" to type,
            "size" to size.toString(),
            "offset" to offset.toString(),
        )
        if (!musicFolderId.isNullOrBlank()) params += "musicFolderId" to musicFolderId
        return get("getAlbumList2", *params.toTypedArray()).albumList2?.album.orEmpty()
    }

    suspend fun getArtists(): List<ArtistID3> =
        get("getArtists").artists?.index.orEmpty().flatMap { it.artist }

    suspend fun getArtist(id: String): ArtistWithAlbums =
        get("getArtist", "id" to id).artist
            ?: throw SubsonicException(70, "Artist not found")

    suspend fun getArtistInfo2(id: String, count: Int = 12): ArtistInfo2 =
        get(
            "getArtistInfo2",
            "id" to id,
            "count" to count.toString(),
            "includeNotPresent" to "true",
        ).artistInfo2 ?: ArtistInfo2()

    suspend fun getAlbum(id: String): AlbumWithSongs =
        get("getAlbum", "id" to id).album
            ?: throw SubsonicException(70, "Album not found")

    suspend fun getTopSongs(artist: String, count: Int = 20): List<Song> =
        get("getTopSongs", "artist" to artist, "count" to count.toString())
            .topSongs?.song.orEmpty()

    suspend fun search3(
        query: String,
        count: Int = 20,
        artistCount: Int = count,
        albumCount: Int = count,
        songCount: Int = count,
        artistOffset: Int = 0,
        albumOffset: Int = 0,
        songOffset: Int = 0,
    ): SearchResult3 =
        get(
            "search3",
            "query" to query,
            "artistCount" to artistCount.toString(),
            "albumCount" to albumCount.toString(),
            "songCount" to songCount.toString(),
            "artistOffset" to artistOffset.toString(),
            "albumOffset" to albumOffset.toString(),
            "songOffset" to songOffset.toString(),
        ).searchResult3 ?: SearchResult3()

    suspend fun getStarred(): Starred2 =
        get("getStarred2").starred2 ?: Starred2()

    suspend fun getGenres(): List<Genre> =
        get("getGenres").genres?.genre.orEmpty()

    suspend fun getSongsByGenre(genre: String, count: Int = 80): List<Song> =
        get(
            "getSongsByGenre",
            "genre" to genre,
            "count" to count.toString(),
        ).songsByGenre?.song.orEmpty()

    suspend fun scrobble(id: String, submission: Boolean) {
        try {
            get("scrobble", "id" to id, "submission" to submission.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Scrobbling must not interrupt playback.
        }
    }

    suspend fun getStarredSongs(): List<Song> =
        get("getStarred2").starred2?.song.orEmpty()

    suspend fun starSong(id: String) {
        get("star", "id" to id)
    }

    suspend fun unstarSong(id: String) {
        get("unstar", "id" to id)
    }

    suspend fun lyricsForSong(song: Song): SongLyrics? {
        val structured = suspendRunCatching { get("getLyricsBySongId", "id" to song.id).lyricsList?.structuredLyrics }
            .getOrNull()
            .orEmpty()
        val best = structured.firstOrNull { it.synced && it.line.isNotEmpty() }
            ?: structured.firstOrNull { it.line.isNotEmpty() }
        if (best != null) {
            return SongLyrics(
                synced = best.synced,
                offsetMs = best.offset,
                lines = best.line.filter { it.value.isNotBlank() },
            )
        }
        val artist = song.artist.orEmpty()
        val title = song.title
        if (artist.isBlank() && title.isBlank()) return null
        val plain = suspendRunCatching {
            get("getLyrics", "artist" to artist, "title" to title).lyrics?.value
        }.getOrNull()
        return parseLrcOrPlain(plain)
    }

    fun coverUrl(coverId: String?, size: Int = 600, credentials: StoredCredentials? = this.credentials): String? {
        if (coverId.isNullOrBlank()) return null
        val creds = credentials ?: return null
        return buildUrl("getCoverArt", mapOf("id" to coverId, "size" to size.toString()), session = true, creds = creds)
            .toString()
    }

    fun streamUrl(songId: String, maxBitRate: Int = 0): String {
        val extra = mutableMapOf(
            "id" to songId,
        )
        if (maxBitRate > 0) extra["maxBitRate"] = maxBitRate.toString()
        else {
            extra["maxBitRate"] = "0"
            extra["format"] = "raw"
        }
        // Fresh salt/token per URL — Subsonic token auth does not expire, but a unique
        // query string forces a new DataSource open when MediaItems are rebuilt on
        // transition / Retry (same recovery energy as playing a new album).
        return buildUrl("stream", extra, session = false).toString()
    }

    /**
     * OpenSubsonic / Subsonic [download](https://opensubsonic.netlify.app/docs/endpoints/download/)
     * URL for the original media file. Same auth as [streamUrl]. Do not persist — query may contain credentials.
     */
    fun downloadUrl(songId: String, creds: StoredCredentials = credentials ?: throw SubsonicException(40, "Not signed in")): String {
        return buildUrl("download", mapOf("id" to songId), session = false, creds = creds).toString()
    }

    private suspend fun get(endpoint: String, vararg params: Pair<String, String>): SubsonicEnvelope =
        request(endpoint, params.toMap(), credentials ?: throw SubsonicException(40, "Not signed in"))

    private suspend fun request(endpoint: String, params: Map<String, String>, creds: StoredCredentials): SubsonicEnvelope =
        withContext(Dispatchers.IO) {
            val url = buildUrl(endpoint, params, session = false, creds = creds)
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            val body = readResponse(request)
            val root = json.decodeFromString(SubsonicRoot.serializer(), body)
            val env = root.response
            if (env.status != "ok") {
                val err = env.error
                throw SubsonicException(err?.code ?: 0, err?.message ?: "Request failed")
            }
            env
        }

    private suspend fun readResponse(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        call.timeout().timeout(60, java.util.concurrent.TimeUnit.SECONDS)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(IOException("Could not connect to the music server", e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.use {
                        if (!it.isSuccessful) throw SubsonicException(0, "HTTP ${it.code}")
                        val source = it.body?.source() ?: throw SubsonicException(0, "Empty server response")
                        // Includes chunked/gzip responses; do not trust Content-Length.
                        if (source.request(MAX_RESPONSE_BYTES + 1)) throw SubsonicException(0, "Server response is too large")
                        source.readUtf8()
                    }
                    continuation.resume(body)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }

    private fun buildUrl(
        endpoint: String,
        extra: Map<String, String>,
        session: Boolean,
        creds: StoredCredentials = credentials ?: throw SubsonicException(40, "Not signed in"),
    ): HttpUrl {
        val root = creds.serverUrl.trim().trimEnd('/').toHttpUrl()
        requireAllowedAuthenticatedUrl(root, creds)
        if (creds.authMode == AuthMode.HexPassword && root.scheme != "https") {
            throw SubsonicException(41, "Password authentication requires HTTPS")
        }
        val builder = root.newBuilder()
            .addPathSegment("rest")
            .addPathSegment(endpoint)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")

        when (creds.authMode) {
            AuthMode.ApiKey -> {
                builder.addQueryParameter("apiKey", creds.apiKey)
            }
            AuthMode.Token -> {
                val salt = if (session) sessionSalt else randomSalt()
                builder.addQueryParameter("u", creds.username)
                builder.addQueryParameter("t", md5(creds.password + salt))
                builder.addQueryParameter("s", salt)
            }
            AuthMode.HexPassword -> {
                builder.addQueryParameter("u", creds.username)
                builder.addQueryParameter("p", "enc:" + toHex(creds.password))
            }
        }

        extra.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    companion object {
        internal const val MAX_RESPONSE_BYTES = 16L * 1024 * 1024
        const val CLIENT_NAME = "Sonveil"
        const val API_VERSION = "1.16.1"

        private val rng = SecureRandom().asKotlinRandom()

        fun randomSalt(length: Int = 16): String {
            val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
            return (1..length).map { alphabet[rng.nextInt(alphabet.length)] }.joinToString("")
        }

        fun md5(value: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            return toHex(digest)
        }

        fun toHex(value: String): String = toHex(value.toByteArray(Charsets.UTF_8))

        fun toHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }
    }
}
