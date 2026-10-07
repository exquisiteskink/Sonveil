package app.sonveil.music.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.remote.AlbumID3
import app.sonveil.music.data.remote.Starred2
import app.sonveil.music.data.remote.suspendRunCatching
import app.sonveil.music.ui.components.AlbumListRow
import app.sonveil.music.ui.components.ScreenTopBar
import app.sonveil.music.ui.components.SectionHeader
import app.sonveil.music.ui.components.SongRow
import app.sonveil.music.ui.theme.LocalClient
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    val client = LocalClient.current
    val player = LocalPlayer.current
    val p = LocalPalette.current
    var starred by remember { mutableStateOf(Starred2()) }
    LaunchedEffect(Unit) {
        starred = suspendRunCatching { client.getStarred() }.getOrDefault(Starred2())
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item { ScreenTopBar("Favorites", onBack) }
        if (starred.album.isEmpty() && starred.song.isEmpty() && starred.artist.isEmpty()) {
            item {
                Text(
                    "Songs, albums, and artists you heart show up here.",
                    color = p.onBackground.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        if (starred.album.isNotEmpty()) {
            item { SectionHeader("Albums") }
            items(starred.album.size) { index ->
                val album: AlbumID3 = starred.album[index]
                AlbumListRow(album, onClick = { onAlbum(album.id) })
            }
        }
        if (starred.artist.isNotEmpty()) {
            item { SectionHeader("Artists") }
            items(starred.artist.size) { index ->
                val artist = starred.artist[index]
                TextButton(onClick = { onArtist(artist.id) }) {
                    Text(artist.name, color = p.onBackground)
                }
            }
        }
        if (starred.song.isNotEmpty()) {
            item {
                SectionHeader("Tracks")
                TextButton(onClick = { player.play(starred.song, 0) }) {
                    Text("Play all")
                }
            }
            itemsIndexed(starred.song, key = { index, song -> "${song.id}-$index" }) { index, song ->
                SongRow(song = song, onClick = { player.play(starred.song, index) })
            }
        }
    }
}
