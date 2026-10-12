package app.sonveil.music.ui.playlist

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.remote.PlaylistWithSongs
import app.sonveil.music.data.remote.formatDuration
import app.sonveil.music.ui.components.CoverArt
import app.sonveil.music.ui.components.ErrorText
import app.sonveil.music.ui.components.HeaderPlay
import app.sonveil.music.ui.components.ScreenTopBar
import app.sonveil.music.ui.components.SongRow
import app.sonveil.music.data.download.DownloadPhase
import app.sonveil.music.ui.theme.LocalContainer
import app.sonveil.music.ui.theme.LocalClient
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer
import app.sonveil.music.ui.theme.LocalPlayerContentInset
import app.sonveil.music.ui.theme.textSecondary
import app.sonveil.music.ui.components.DetailLoadingScreen
import app.sonveil.music.ui.components.libraryError

@Composable
fun PlaylistScreen(playlistId: String, onBack: () -> Unit) {
    val client = LocalClient.current
    val player = LocalPlayer.current
    val container = LocalContainer.current
    val dlState by container.downloads.state.collectAsState()
    val p = LocalPalette.current
    var playlist by remember(playlistId) { mutableStateOf<PlaylistWithSongs?>(null) }
    var error by remember(playlistId) { mutableStateOf<String?>(null) }

    var retryEpoch by remember(playlistId) { mutableIntStateOf(0) }
    LaunchedEffect(playlistId, retryEpoch) {
        error = null
        try {
            playlist = client.getPlaylist(playlistId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = libraryError(e)
        }
    }

    val current = playlist
    if (current == null) {
        DetailLoadingScreen("Playlist", onBack, error, { retryEpoch++ })
        return
    }

    val stats = "${current.entry.size} tracks  –  ${formatDuration(current.duration)}"

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(bottom = LocalPlayerContentInset.current),
    ) {
        item {
            ScreenTopBar(current.name, onBack)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CoverArt(
                    current.coverArt,
                    Modifier
                        .size(132.dp)
                        .shadow(10.dp, RoundedCornerShape(8.dp))
                        .border(2.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                    current.name,
                    corner = 8.dp,
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (current.entry.isNotEmpty()) {
                        HeaderPlay(onClick = { player.play(current.entry, 0) }, size = 64.dp)
                        Spacer(Modifier.height(8.dp))
                        IconButton(onClick = { player.play(current.entry.shuffled(), 0) }) {
                            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = p.textSecondary)
                        }
                        IconButton(
                            onClick = {
                                container.downloads.enqueuePlaylist(playlistId, current.entry, current.name)
                            },
                        ) {
                            val tint = when (dlState.phase) {
                                DownloadPhase.Running -> p.primary
                                else -> p.textSecondary
                            }
                            Icon(Icons.Rounded.Download, "Download playlist", tint = tint)
                        }
                    }
                }
            }
            Text(
                stats,
                color = p.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (dlState.phase == DownloadPhase.Running ||
                dlState.phase == DownloadPhase.PausedWifi ||
                dlState.phase == DownloadPhase.Failed
            ) {
                val status = when (dlState.phase) {
                    DownloadPhase.Running -> "Downloading ${dlState.done}/${dlState.total}" +
                        (dlState.currentTitle?.let { " · $it" } ?: "")
                    else -> dlState.message.orEmpty()
                }
                if (status.isNotBlank()) {
                    Text(
                        status,
                        color = p.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(1.dp)
                    .background(p.onBackground.copy(alpha = 0.22f)),
            )
        }
        itemsIndexed(current.entry) { i, song ->
            SongRow(
                song = song,
                onClick = { player.play(current.entry, i) },
                showCover = false,
                rank = i + 1,
            )
        }
    }
}
