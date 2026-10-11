package app.sonveil.music.data.player.auto

import java.util.Base64

/** IDs carry parent and occurrence, so legacy ID-only requests retain duplicate tracks. */
object AutoBrowseIds {
    const val ROOT = "sonveil_root"
    const val PLAYLISTS = "sonveil_playlists"
    const val RECENT = "sonveil_recent"
    const val FAVORITES = "sonveil_favorites"
    const val NEWEST = "sonveil_newest"
    const val LIBRARY = "sonveil_library"
    const val ARTISTS = "sonveil_artists"
    const val VOICE = "sonveil_voice"
    const val EXTRA_PARENT = "sonveil.parent_id"

    /** Pre-rebrand browse IDs (PR #8). Still accepted so in-flight Auto sessions keep resolving. */
    private const val LEGACY_ROOT = "auralis_root"
    private const val LEGACY_PLAYLISTS = "auralis_playlists"
    private const val LEGACY_RECENT = "auralis_recent"
    private const val LEGACY_FAVORITES = "auralis_favorites"
    private const val LEGACY_NEWEST = "auralis_newest"
    private const val LEGACY_EXTRA_PARENT = "auralis.parent_id"

    fun normalizeParentId(mediaId: String): String = when (mediaId) {
        LEGACY_ROOT -> ROOT
        LEGACY_PLAYLISTS -> PLAYLISTS
        LEGACY_RECENT -> RECENT
        LEGACY_FAVORITES -> FAVORITES
        LEGACY_NEWEST -> NEWEST
        else -> mediaId
    }

    fun isRoot(mediaId: String): Boolean = normalizeParentId(mediaId) == ROOT

    fun isFavorites(mediaId: String): Boolean = normalizeParentId(mediaId) == FAVORITES

    fun parentExtraKeys(): Array<String> = arrayOf(EXTRA_PARENT, LEGACY_EXTRA_PARENT)

    private fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decode(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8).takeIf { encode(it) == value }
    }.getOrNull()

    fun playlist(id: String) = "playlist/${encode(id)}"
    fun album(id: String) = "album/${encode(id)}"
    fun artist(id: String) = "artist/${encode(id)}"
    fun song(id: String, parent: String, index: Int) = "song/${encode(parent)}/$index/${encode(id)}"
    fun parsePlaylistId(mediaId: String): String? = parseFolder(mediaId, "playlist/")
    fun parseAlbumId(mediaId: String): String? = parseFolder(mediaId, "album/")
    fun parseArtistId(mediaId: String): String? = parseFolder(mediaId, "artist/")
    private fun parseFolder(id: String, prefix: String): String? =
        id.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.let(::decode)?.takeIf { it.isNotEmpty() }

    data class SongRef(val songId: String, val parent: String, val index: Int)
    fun parseSong(mediaId: String): SongRef? {
        val parts = mediaId.split('/')
        if (parts.size != 4 || parts[0] != "song") return null
        val parent = decode(parts[1])?.let(::normalizeParentId) ?: return null
        if (parent != FAVORITES && parent != VOICE && parsePlaylistId(parent) == null && parseAlbumId(parent) == null) return null
        val index = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val song = decode(parts[3])?.takeIf { it.isNotEmpty() } ?: return null
        return SongRef(song, parent, index)
    }
}
