package app.sonveil.music.ui.home

import app.sonveil.music.data.remote.suspendRunCatching
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.remote.AlbumID3
import app.sonveil.music.data.remote.ArtistID3
import app.sonveil.music.data.remote.Genre
import app.sonveil.music.data.remote.Playlist
import app.sonveil.music.data.remote.Song
import app.sonveil.music.ui.components.ArtistTile
import app.sonveil.music.ui.components.ErrorText
import app.sonveil.music.ui.components.GenreChip
import app.sonveil.music.ui.components.HorizontalAlbums
import app.sonveil.music.ui.components.PlaylistChip
import app.sonveil.music.ui.components.SectionHeader
import app.sonveil.music.ui.components.SongRow
import app.sonveil.music.ui.theme.LocalClient
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer
import app.sonveil.music.ui.theme.LocalPlayerState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onDownloads: () -> Unit,
    onAlbums: () -> Unit,
    onFavorites: () -> Unit,
    onPlaylist: (String) -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    val client = LocalClient.current
    val player = LocalPlayer.current
    val playerState = LocalPlayerState.current
    val favoriteEpoch by remember(playerState) {
        derivedStateOf { playerState.value.favoriteEpoch }
    }
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var recent by remember { mutableStateOf<List<AlbumID3>>(emptyList()) }
    var newest by remember { mutableStateOf<List<AlbumID3>>(emptyList()) }
    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        try {
            coroutineScope {
                val pDef = async { suspendRunCatching { client.getPlaylists() }.getOrDefault(emptyList()) }
                val rDef = async { suspendRunCatching { client.getAlbumList2("recent", 24) }.getOrDefault(emptyList()) }
                val nDef = async { suspendRunCatching { client.getAlbumList2("newest", 24) }.getOrDefault(emptyList()) }
                val gDef = async { suspendRunCatching { client.getGenres() }.getOrDefault(emptyList()) }
                val fDef = async { suspendRunCatching { client.getStarredSongs() }.getOrDefault(emptyList()) }
                playlists = pDef.await()
                recent = rDef.await()
                newest = nDef.await()
                genres = gDef.await().filter { it.value.isNotBlank() }.sortedByDescending { it.songCount }.take(24)
                favorites = fDef.await()
                if (playlists.isEmpty() && recent.isEmpty() && newest.isEmpty()) {
                    error = "Could not load library"
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(favoriteEpoch) {
        if (favoriteEpoch > 0) {
            favorites = suspendRunCatching { client.getStarredSongs() }.getOrDefault(favorites)
        }
    }

    val recentArtists = remember(recent) {
        val seen = LinkedHashSet<String>()
        recent.mapNotNull { album ->
            val id = album.artistId ?: return@mapNotNull null
            if (!seen.add(id)) null
            else ArtistID3(
                id = id,
                name = album.artist ?: "Artist",
                coverArt = album.coverArt,
                albumCount = 0,
            )
        }
    }

    fun playGenre(genre: Genre) {
        scope.launch {
            val songs = suspendRunCatching { client.getSongsByGenre(genre.value, 80) }.getOrDefault(emptyList())
            if (songs.isNotEmpty()) player.play(songs.shuffled(), 0)
        }
    }


    PullToRefreshBox(isRefreshing = loading, onRefresh = { scope.launch { load() } }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 64.dp),
        ) {
            Text(
                "Home",
                color = p.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            )
            if (error != null && playlists.isEmpty() && recent.isEmpty()) {
                ErrorText(error ?: "Could not load library")
            }

            androidx.compose.material3.TextButton(onClick = onDownloads) {
                Text("Downloads — listen offline")
            }

            // Your playlists
            SectionHeader("Your playlists")
            if (playlists.isEmpty() && !loading) {
                Text(
                    "No playlists yet",
                    color = p.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    playlists.forEach { playlist ->
                        PlaylistChip(playlist, onClick = { onPlaylist(playlist.id) })
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // Recently played albums
            if (recent.isNotEmpty()) {
                SectionHeader("Recently played")
                HorizontalAlbums(recent) { onAlbum(it.id) }
                Spacer(Modifier.height(18.dp))
            }

            // Recently added albums
            if (newest.isNotEmpty()) {
                SectionHeader("Recently added albums", onClick = onAlbums)
                HorizontalAlbums(newest) { onAlbum(it.id) }
                Spacer(Modifier.height(18.dp))
            } else if (!loading) {
                SectionHeader("Albums", onClick = onAlbums)
            }

            // Favorite tracks
            if (favorites.isNotEmpty()) {
                SectionHeader("Favorite tracks", onClick = onFavorites)
                favorites.take(5).forEachIndexed { i, song ->
                    SongRow(
                        song = song,
                        onClick = { player.play(favorites, i) },
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            // Artists in rotation
            if (recentArtists.isNotEmpty()) {
                SectionHeader("Artists in rotation")
                val artistState = rememberLazyListState()
                LazyRow(
                    state = artistState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = artistState),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recentArtists, key = { "rot-${it.id}" }) { artist ->
                        Column(Modifier.width(96.dp)) {
                            ArtistTile(artist, onClick = { onArtist(artist.id) }, circular = true)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // Genres
            if (genres.isNotEmpty()) {
                SectionHeader("Genres")
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    genres.forEach { genre ->
                        GenreChip(genre, onClick = { playGenre(genre) })
                    }
                }
            }
        }
    }
}
