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
    fun rootChildren(): List<MediaItem> = listOf(
        items.playlistsRoot(), items.recentRoot(), items.favoritesRoot(), items.libraryRoot(),
    )

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
        if (!ensureCredentials()) return emptyList()
        return when (parent) {
            AutoBrowseIds.LIBRARY -> listOf(items.artistsRoot(), items.newestRoot())
            // getArtists contains the complete Navidrome index; do not truncate the car library.
            AutoBrowseIds.ARTISTS -> client.getArtists().map(items::artist)
            AutoBrowseIds.PLAYLISTS -> client.getPlaylists().map(items::playlist)
            AutoBrowseIds.RECENT -> client.getAlbumList2("recent", 48).map(items::album)
            AutoBrowseIds.NEWEST -> client.getAlbumList2("newest", 48).map(items::album)
            else -> AutoBrowseIds.parseArtistId(parent)?.let { artistId ->
                client.getArtist(artistId).album.map(items::album)
            } ?: items.playableSongs(songsIn(parent), parent)
        }
    }

    suspend fun item(mediaId: String): MediaItem? {
        val id = AutoBrowseIds.normalizeParentId(mediaId)
        rootChildren().firstOrNull { it.mediaId == id }?.let { return it }
        if (id == AutoBrowseIds.ARTISTS) return items.artistsRoot()
        // Keep old Recently added IDs usable for connected hosts after changing the tabs.
        if (id == AutoBrowseIds.NEWEST) return items.newestRoot()
        if (AutoBrowseIds.isRoot(id)) return rootItem()
        if (!ensureCredentials()) return null
        AutoBrowseIds.parseArtistId(id)?.let { artistId ->
            val artist = client.getArtist(artistId)
            return items.artist(ArtistID3(
                id = artist.id,
                name = artist.name,
                coverArt = artist.coverArt,
                albumCount = artist.albumCount,
            ))
        }
        AutoBrowseIds.parsePlaylistId(id)?.let { playlistId ->
            val pl = client.getPlaylist(playlistId)
            return items.playlist(Playlist(id = pl.id, name = pl.name, songCount = pl.songCount, coverArt = pl.coverArt))
        }
        AutoBrowseIds.parseAlbumId(id)?.let { albumId ->
            val album = client.getAlbum(albumId)
            return items.album(AlbumID3(id = album.id, name = album.displayName, artist = album.artist, coverArt = album.coverArt))
        }
        val resolved = resolver.resolve(mediaId) ?: return null
        return items.song(resolved.songs[resolved.startIndex], resolved.parent, resolved.startIndex)
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
}
