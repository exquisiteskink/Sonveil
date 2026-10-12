package app.sonveil.music.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.player.PlayerUiState
import app.sonveil.music.data.remote.formatDurationMs
import app.sonveil.music.ui.theme.*

/** Pure UI: injected artwork/actions keep layout tests independent of network/audio services. */
@Composable
internal fun NowPlayingContent(
    ui: PlayerUiState, positionMs: Long, lyricsOpen: Boolean,
    onArtist: (String) -> Unit, onClose: () -> Unit, onOpenQueue: () -> Unit,
    onSeek: (Long) -> Unit, onRetry: () -> Unit, onToggleFavorite: () -> Unit, onToggleLyrics: () -> Unit,
    artwork: @Composable (Modifier) -> Unit, controls: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    closeModifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val song = ui.current ?: return
    BoxWithConstraints(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .padding(horizontal = SonveilSize.PlayerGutter)) {
        val layout = playerLayout(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
        val metadata: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDurationMs(positionMs), color = p.textSecondary,
                    style = MaterialTheme.typography.bodySmall)
                FlatSeekBar(positionMs, ui.durationMs, onSeek, Modifier.weight(1f).padding(horizontal = 8.dp))
                Text(formatDurationMs(ui.durationMs), color = p.textSecondary,
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { song.artistId?.let(onArtist) }, enabled = song.artistId != null,
                modifier = Modifier.heightIn(min = SonveilSize.Action)) {
                Text(song.artist.orEmpty(), color = p.onBackground, style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center)
            }
            Text(song.title, color = p.onBackground, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
            song.album?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = p.textSecondary, style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            PlaybackStatus(ui)
            ui.playbackError?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 8.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                }
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = SonveilSize.Action)) { Text("Retry playback") }
            }
            MetaRow(song.codecLabel, song.bitDepth, song.sampleRateLabel, ui.isFavorite(song), lyricsOpen,
                onToggleFavorite, onToggleLyrics)
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onClose, modifier = closeModifier) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = p.textSecondary, modifier = Modifier.size(28.dp))
            }
            if (layout.twoPane) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        // Use the measured pane after transport/queue allocation, not the full window.
                        // Independent width/height coercion would otherwise crop square artwork.
                        val artSize = minOf(layout.artSize.dp, maxWidth, maxHeight)
                        artwork(Modifier.size(artSize))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally) { metadata() }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    artwork(Modifier.size(layout.artSize.dp))
                    Spacer(Modifier.height(12.dp))
                    metadata()
                    Spacer(Modifier.height(8.dp))
                }
            }
            // Never let art or long text push the transport and queue controls off-screen.
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { controls() }
            TextButton(onClick = onOpenQueue, modifier = Modifier.fillMaxWidth().heightIn(min = SonveilSize.Action)) {
                Text("Up next · ${ui.upNext.size} songs", color = p.textSecondary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
