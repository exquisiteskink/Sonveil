package app.sonveil.music.ui.download

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sonveil.music.data.remote.Song
import app.sonveil.music.ui.components.SongRow
import app.sonveil.music.ui.theme.LocalContainer
import app.sonveil.music.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val palette = LocalPalette.current
    val downloadState by container.downloads.state.collectAsState()
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(downloadState) {
        songs = withContext(Dispatchers.IO) {
            container.client.credentials?.let { creds ->
                container.downloadStore.availableSongs(container.downloadStore.serverKey(creds))
            }.orEmpty()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Downloads", color = palette.onBackground, modifier = Modifier.padding(16.dp))
            if (songs.isEmpty()) Text("Download an album or playlist to listen offline.",
                color = palette.onBackground, modifier = Modifier.padding(16.dp))
        }
        val groups = songs.groupBy { it.album?.ifBlank { null } ?: "Saved tracks" }
        groups.forEach { (album, tracks) ->
            item { Text(album, color = palette.onBackground, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            itemsIndexed(tracks, key = { _, song -> song.id }) { _, song ->
                val index = songs.indexOfFirst { it.id == song.id }
                SongRow(
                    song = song,
                    onClick = { if (index >= 0) container.player.play(songs, index) },
                    showCover = false,
                )
            }
        }
    }
}
