package app.sonveil.music.data.player.auto

import android.os.Bundle
import android.provider.MediaStore
import app.sonveil.music.data.remote.Song
import app.sonveil.music.data.remote.SubsonicClient
import app.sonveil.music.data.remote.suspendRunCatching

/** Resolves Assistant/Gemini play-from-search requests against the signed-in library. */
internal class AutoVoiceSearch(private val client: SubsonicClient) {
    suspend fun songs(query: String, extras: Bundle?): List<Song> {
        val title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)?.trim().orEmpty()
        val album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)?.trim().orEmpty()
        val artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.trim().orEmpty()
        val playlist = extras?.getString(MediaStore.EXTRA_MEDIA_PLAYLIST)?.trim().orEmpty()
        val genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE)?.trim().orEmpty()
        val q = query.trim()

        if (playlist.isNotEmpty()) return playlistSongs(playlist)
        if (genre.isNotEmpty()) return client.getSongsByGenre(genre, 80)
        if (album.isNotEmpty()) return albumSongs(album, artist)
        if (title.isNotEmpty()) return titleSongs(title, artist)
        if (artist.isNotEmpty()) return artistSongs(artist)
        if (q.isEmpty()) return defaultSongs()

        // Search3 is supported by Navidrome, Subsonic and OpenSubsonic servers.
        val hits = client.search3(q, artistCount = 10, albumCount = 10, songCount = 30)
        bestMatch(hits.song, q) { it.title }?.let { return listOf(it) }
        bestMatch(hits.album, q) { it.displayName }?.let { return client.getAlbum(it.id).song }
        bestMatch(hits.artist, q) { it.name }?.let { return artistSongs(it.name) }
        bestMatch(client.getPlaylists(), q) { it.name }?.let { return client.getPlaylist(it.id).entry }
        val matchedGenre = bestMatch(client.getGenres(), q) { it.value }
        if (matchedGenre != null) return client.getSongsByGenre(matchedGenre.value, 80)
        return emptyList()
    }

    private suspend fun playlistSongs(name: String): List<Song> {
        val playlist = bestMatch(client.getPlaylists(), name) { it.name } ?: return emptyList()
        return client.getPlaylist(playlist.id).entry
    }

    private suspend fun albumSongs(name: String, artist: String): List<Song> {
        val hits = client.search3(name, artistCount = 0, albumCount = 20, songCount = 0).album
        val candidates = if (artist.isBlank()) hits else hits.filter { it.artist.equals(artist, ignoreCase = true) }
        val album = bestMatch(candidates, name) { it.displayName } ?: return emptyList()
        return client.getAlbum(album.id).song
    }

    private suspend fun titleSongs(name: String, artist: String): List<Song> {
        val hits = client.search3(name, artistCount = 0, albumCount = 0, songCount = 40).song
        val candidates = if (artist.isBlank()) hits else hits.filter { it.artist.equals(artist, ignoreCase = true) }
        return bestMatch(candidates, name) { it.title }?.let(::listOf).orEmpty()
    }

    private suspend fun artistSongs(name: String): List<Song> {
        // Some Subsonic servers omit getTopSongs even though they support search3.
        val top = suspendRunCatching { client.getTopSongs(name, 40) }.getOrDefault(emptyList())
        if (top.isNotEmpty()) return top
        return client.search3(name, artistCount = 0, albumCount = 0, songCount = 80).song
            .filter { it.artist.equals(name, ignoreCase = true) }
    }

    private suspend fun defaultSongs(): List<Song> {
        client.getStarredSongs().takeIf { it.isNotEmpty() }?.let { return it.take(80) }
        val recent = client.getAlbumList2("recent", 1).firstOrNull() ?: return emptyList()
        return client.getAlbum(recent.id).song
    }
}

/** Prefer an exact name, then a prefix, then a substring. Never play an unrelated hit. */
internal fun <T> bestMatch(items: List<T>, query: String, name: (T) -> String): T? {
    val wanted = query.trim()
    if (wanted.isEmpty()) return null
    return items.firstOrNull { name(it).equals(wanted, ignoreCase = true) }
        ?: items.firstOrNull { name(it).startsWith(wanted, ignoreCase = true) }
        ?: items.firstOrNull { name(it).contains(wanted, ignoreCase = true) }
}
