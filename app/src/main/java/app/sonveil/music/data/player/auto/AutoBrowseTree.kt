package app.sonveil.music.data.player.auto

import android.os.Bundle
import androidx.media3.common.MediaItem
import app.sonveil.music.AppContainer
import app.sonveil.music.data.remote.AlbumID3
import app.sonveil.music.data.remote.ArtistID3
import app.sonveil.music.data.remote.Playlist
import app.sonveil.music.data.remote.Song

internal class AutoBrowseTree(
    private val container: AppContainer,
    private val items: AutoMediaItemFactory,
) {
    private val client get() = container.client
    private val resolver = AutoQueueResolver(::songsIn)
    private val voiceSearch = AutoVoiceSearch(client)
    private var voiceSongs: List<Song> = emptyList()

    private suspend fun ensureCredentials(): Boolean {
        if (client.credentials == null) container.restoreSession()
        return client.credentials != null
    }

    fun rootItem(): MediaItem = items.root()
    /**
     * Android Auto renders at most four root tabs (extra roots are dropped), so the root is
     * Home / Artists / Albums / Playlists. Home groups the former Recently played, Favorites
     * and Recently added tabs, whose IDs are unchanged.
     */
    fun rootChildren(): List<MediaItem> = AutoBrowseTree.rootOrder.map(::rootFolder)

    fun homeChildren(): List<MediaItem> = listOf(items.recentRoot(), items.favoritesRoot(), items.newestRoot())

    private fun rootFolder(id: String): MediaItem = when (id) {
        AutoBrowseIds.HOME -> items.homeRoot()
        AutoBrowseIds.ARTISTS -> items.artistsRoot()
        AutoBrowseIds.ALBUMS -> items.albumsRoot()
        else -> items.playlistsRoot()
    }

    /** True when the parent is the AA tab root (no server call required). */
    fun isRootId(parentId: String): Boolean = AutoBrowseIds.isRoot(parentId)

    suspend fun hasCredentials(): Boolean = ensureCredentials()

    suspend fun songsIn(parent: String): List<Song> {
        val normalized = AutoBrowseIds.normalizeParentId(parent)
        return when {
            normalized == AutoBrowseIds.VOICE -> voiceSongs
            AutoBrowseIds.isFavorites(normalized) -> client.getStarredSongs()
            AutoBrowseIds.parsePlaylistId(normalized) != null ->
                client.getPlaylist(AutoBrowseIds.parsePlaylistId(normalized)!!).entry
            AutoBrowseIds.parseAlbumId(normalized) != null ->
                client.getAlbum(AutoBrowseIds.parseAlbumId(normalized)!!).song
            else -> emptyList()
        }
    }

    suspend fun childrenOf(parentId: String): List<MediaItem> {
        val parent = AutoBrowseIds.normalizeParentId(parentId)
        // Root tabs must load without waiting on login so AA can paint the app after select.
        if (AutoBrowseIds.isRoot(parent)) return rootChildren()
        if (parent == AutoBrowseIds.HOME) return homeChildren()
        if (!ensureCredentials()) return emptyList()
        AutoBrowseIds.parseArtistId(parent)?.let { artistId ->
            return client.getArtist(artistId).album.map(items::album)
        }
        return when (parent) {
            AutoBrowseIds.ARTISTS -> client.getArtists().sortedBy { it.name.lowercase() }.map(items::artist)
            AutoBrowseIds.ALBUMS -> allAlbums().map(items::album)
            AutoBrowseIds.PLAYLISTS -> client.getPlaylists().map(items::playlist)
            AutoBrowseIds.RECENT -> client.getAlbumList2("recent", 48).map(items::album)
            AutoBrowseIds.NEWEST -> client.getAlbumList2("newest", 48).map(items::album)
            else -> items.playableSongs(songsIn(parent), parent)
        }
    }

    suspend fun item(mediaId: String): MediaItem? {
        val id = AutoBrowseIds.normalizeParentId(mediaId)
        (rootChildren() + homeChildren()).firstOrNull { it.mediaId == id }?.let { return it }
        if (AutoBrowseIds.isRoot(id)) return rootItem()
        if (!ensureCredentials()) return null
        AutoBrowseIds.parsePlaylistId(id)?.let { playlistId ->
            val pl = client.getPlaylist(playlistId)
            return items.playlist(Playlist(id = pl.id, name = pl.name, songCount = pl.songCount, coverArt = pl.coverArt))
        }
        AutoBrowseIds.parseArtistId(id)?.let { artistId ->
            val artist = client.getArtist(artistId)
            return items.artist(ArtistID3(id = artist.id, name = artist.name, coverArt = artist.coverArt, albumCount = artist.albumCount))
        }
        AutoBrowseIds.parseAlbumId(id)?.let { albumId ->
            val album = client.getAlbum(albumId)
            return items.album(AlbumID3(id = album.id, name = album.displayName, artist = album.artist, coverArt = album.coverArt))
        }
        val resolved = resolver.resolve(mediaId) ?: return null
        return items.song(resolved.songs[resolved.startIndex], resolved.parent, resolved.startIndex)
    }

    /** Alphabetical albums, fetched in server pages up to [MAX_ALBUMS] (AA lists are not infinite). */
    private suspend fun allAlbums(): List<AlbumID3> {
        val out = ArrayList<AlbumID3>()
        while (out.size < MAX_ALBUMS) {
            val page = client.getAlbumList2("alphabeticalByName", ALBUM_PAGE, out.size)
            out += page
            if (page.size < ALBUM_PAGE) break
        }
        return out.take(MAX_ALBUMS)
    }

    suspend fun resolveQueue(requested: List<MediaItem>, startIndex: Int): AutoQueueResolver.Queue? {
        if (requested.isEmpty() || !ensureCredentials()) return null
        val focus = requested.getOrNull(if (startIndex < 0) 0 else startIndex) ?: return null
        return resolver.resolve(focus.mediaId)
    }

    suspend fun resolveVoice(query: String, extras: Bundle?): AutoQueueResolver.Queue? {
        if (!ensureCredentials()) return null
        val songs = voiceSearch.songs(query, extras).take(80)
        if (songs.isEmpty()) return null
        voiceSongs = songs
        return AutoQueueResolver.Queue(songs, 0, AutoBrowseIds.VOICE)
    }

    companion object {
        val rootOrder = listOf(AutoBrowseIds.HOME, AutoBrowseIds.ARTISTS, AutoBrowseIds.ALBUMS, AutoBrowseIds.PLAYLISTS)
        private const val ALBUM_PAGE = 500
        private const val MAX_ALBUMS = 1000
    }
}
