package app.sonveil.music.data.player.auto

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.sonveil.music.R
import app.sonveil.music.data.art.CoverArtContentProvider
import app.sonveil.music.data.player.PlayerSettings
import app.sonveil.music.data.remote.AlbumID3
import app.sonveil.music.data.remote.Playlist
import app.sonveil.music.data.remote.Song
import app.sonveil.music.data.remote.SubsonicClient

/**
 * Builds browsable / playable MediaItems for Auto, mirroring [app.sonveil.music.data.player.PlayerController]
 * stream extras so leaf playback reuses the same Subsonic stream URLs.
 *
 * Artwork for playlist/album/song items uses [CoverArtContentProvider] `content://` URIs
 * (Android Auto / AAOS require local content or android.resource URIs — not HTTP).
 * Root tab icons stay on `android.resource://`.
 */
class AutoMediaItemFactory(
    private val client: SubsonicClient,
    private val packageName: String,
    private val bitrate: () -> Int,
) {
    fun root(): MediaItem = folder(
        mediaId = AutoBrowseIds.ROOT,
        title = "Sonveil",
        isPlayable = false,
    )

    fun playlistsRoot(): MediaItem = folder(
        mediaId = AutoBrowseIds.PLAYLISTS,
        title = "Playlists",
        iconRes = R.drawable.ic_auto_playlists,
    )

    fun recentRoot(): MediaItem = folder(
        mediaId = AutoBrowseIds.RECENT,
        title = "Recently played",
        iconRes = R.drawable.ic_auto_recent,
    )

    fun favoritesRoot(): MediaItem = folder(
        mediaId = AutoBrowseIds.FAVORITES,
        title = "Favorites",
        iconRes = R.drawable.ic_auto_favorites,
    )

    fun newestRoot(): MediaItem = folder(
        mediaId = AutoBrowseIds.NEWEST,
        title = "Recently added",
        iconRes = R.drawable.ic_auto_newest,
    )

    fun playlist(pl: Playlist): MediaItem = folder(
        mediaId = AutoBrowseIds.playlist(pl.id),
        title = pl.name.ifBlank { "Playlist" },
        subtitle = if (pl.songCount > 0) "${pl.songCount} songs" else null,
        artworkId = pl.coverArt,
        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
        isPlayable = true,
    )

    fun album(album: AlbumID3): MediaItem = folder(
        mediaId = AutoBrowseIds.album(album.id),
        title = album.displayName,
        subtitle = album.artist,
        artworkId = album.coverArt,
        mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
        isPlayable = true,
    )

    fun song(song: Song, parentId: String, index: Int): MediaItem {
        val art = CoverArtContentProvider.authorizedContentUri(song.coverArt, 800, client.credentials, client.artworkNamespace)
        val extras = Bundle().apply {
            putString("app_name", "Sonveil")
            putString("com.android.music.musicsource", "Sonveil")
            if (parentId.isNotBlank()) putString(AutoBrowseIds.EXTRA_PARENT, parentId)
            song.replayGain?.trackGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_TRACK, it) }
            song.replayGain?.albumGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_ALBUM, it) }
            song.replayGain?.trackPeak?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_TRACK_PEAK, it) }
            song.replayGain?.albumPeak?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_ALBUM_PEAK, it) }
            song.replayGain?.fallbackGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_FALLBACK, it) }
        }
        return MediaItem.Builder()
            .setMediaId(AutoBrowseIds.song(song.id, parentId, index))
            .setUri(app.sonveil.music.data.player.PlaybackStreamUri.build(song.id, bitrate(), java.util.UUID.randomUUID().toString()))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title.ifBlank { "Track" })
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setSubtitle(song.artist)
                    .setDescription("Sonveil")
                    .setWriter("Sonveil")
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setArtworkUri(art)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    /** Playable MediaItem list matching phone [PlayerController] stream path. */
    fun playableSongs(songs: List<Song>, parentId: String): List<MediaItem> =
        songs.mapIndexed { index, song -> song(song, parentId, index) }

    private fun folder(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artworkId: String? = null,
        iconRes: Int? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        isPlayable: Boolean = false,
    ): MediaItem {
        val art = when {
            artworkId != null -> CoverArtContentProvider.authorizedContentUri(artworkId, 400, client.credentials, client.artworkNamespace)
            iconRes != null -> Uri.parse("android.resource://$packageName/$iconRes")
            else -> null
        }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription("Sonveil")
                    .setWriter("Sonveil")
                    .setMediaType(mediaType)
                    .setIsBrowsable(true)
                    .setIsPlayable(isPlayable)
                    .setArtworkUri(art)
                    .build(),
            )
            .build()
    }

}
