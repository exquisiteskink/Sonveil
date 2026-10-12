package app.sonveil.music.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sonveil.music.data.remote.Playlist
import app.sonveil.music.ui.components.PlaylistChip
import app.sonveil.music.ui.components.SectionHeader
import app.sonveil.music.ui.theme.SonveilSize

internal const val PlaylistPreviewLimit = 6

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaylistPreview(playlists: List<Playlist>, onPlaylist: (String) -> Unit, onSeeAll: () -> Unit) {
    Column {
        SectionHeader("Your playlists")
        if (playlists.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = SonveilSize.PageGutter),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = SonveilSize.PageGutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                playlists.take(PlaylistPreviewLimit).forEach { playlist ->
                    PlaylistChip(playlist, onClick = { onPlaylist(playlist.id) })
                }
            }
        }
    }
}
