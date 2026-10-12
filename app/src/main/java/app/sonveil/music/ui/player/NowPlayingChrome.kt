package app.sonveil.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.sonveil.music.data.player.PlayerUiState
import app.sonveil.music.ui.components.AlbumCoverArt
import app.sonveil.music.ui.components.SongRow
import app.sonveil.music.ui.theme.SonveilMotion
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer
import app.sonveil.music.ui.theme.seekColors
import app.sonveil.music.ui.theme.sonveilGlass
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import app.sonveil.music.ui.theme.textSecondary
import app.sonveil.music.ui.theme.focusRing
import app.sonveil.music.ui.theme.SonveilSize

@Composable
internal fun MetaRow(
    codec: String?,
    bitDepth: Int,
    sampleRate: String?,
    favorite: Boolean,
    lyricsOpen: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleLyrics: () -> Unit,
) {
    val p = LocalPalette.current
    val audioFormat = listOfNotNull(codec?.takeIf { it.isNotBlank() },
        bitDepth.takeIf { it > 0 }?.let { "$it-bit" }, sampleRate?.takeIf { it.isNotBlank() }).joinToString(" · ")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (audioFormat.isNotEmpty()) Text("Source file · $audioFormat", color = p.textSecondary,
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onToggleLyrics, modifier = Modifier.size(SonveilSize.Action).semantics {
                stateDescription = if (lyricsOpen) "Lyrics shown" else "Lyrics hidden"
            }) {
                Icon(Icons.Rounded.Lyrics, if (lyricsOpen) "Hide lyrics" else "Show lyrics",
                    tint = if (lyricsOpen) p.primary else p.textSecondary, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(SonveilSize.Action).semantics {
                stateDescription = if (favorite) "Favorite" else "Not a favorite"
            }) {
                Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (favorite) p.primary else p.textSecondary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
internal fun LyricsPane(
    lyrics: app.sonveil.music.data.remote.SongLyrics?,
    positionMs: Long,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    val lines = lyrics?.lines.orEmpty()
    val t = positionMs - (lyrics?.offsetMs ?: 0)
    val active = if (lyrics?.synced == true && lines.isNotEmpty()) {
        lines.indexOfLast { it.start <= t }.coerceAtLeast(0)
    } else -1
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) runCatching { listState.animateScrollToItem(active.coerceAtLeast(0)) }
    }
    Column(
        modifier
            .sonveilGlass(p, 12.dp)
            .clickable(onClick = onClose)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No lyrics on the server for this track",
                    color = p.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lines) { i, line ->
                    val current = i == active
                    Text(
                        line.value,
                        color = if (current) p.onBackground else p.textSecondary,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = if (current) 18.sp else 15.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun QueuePage(
    ui: PlayerUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    closeModifier: Modifier = Modifier,
) {
    val player = LocalPlayer.current
    val p = LocalPalette.current
    val song = ui.current ?: return

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            headerModifier
                .fillMaxWidth()
                .clickable(onClick = onClose)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = closeModifier) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Back to player", tint = p.onBackground)
            }
            AlbumCoverArt(song.albumId, Modifier.size(44.dp), song.title, corner = 4.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(song.artist.orEmpty(), color = p.textSecondary, maxLines = 1, fontSize = 12.sp)
            }
            IconButton(onClick = { player.playPause() }) {
                Icon(
                    if (ui.showPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ui.showPause) "Pause" else "Play",
                    tint = p.onBackground,
                )
            }
        }
        Text(
            "UP NEXT",
            color = p.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 1.1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(ui.upNext, key = { i, s -> "${s.id}-$i" }) { i, s ->
                SongRow(
                    song = s,
                    onClick = { player.playFromQueue(ui.upNextIndices[i]) },
                    playing = false,
                    showCover = true,
                )
            }
            if (ui.upNext.isEmpty()) {
                item {
                    Text(
                        "Nothing else queued",
                        color = p.textSecondary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PositionAwareMiniBar(
    ui: PlayerUiState,
    position: State<Long>,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit = onPlayPause,
    onHeightChanged: (Int) -> Unit = {},
) {
    MiniBar(
        ui = ui,
        positionMs = position.value,
        modifier = modifier,
        onPlayPause = onPlayPause,
        onRetry = onRetry,
        onHeightChanged = onHeightChanged,
    )
}

@Composable
internal fun MiniBar(
    ui: PlayerUiState,
    positionMs: Long,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit = onPlayPause,
    onHeightChanged: (Int) -> Unit = {},
) {
    val p = LocalPalette.current
    val song = ui.current ?: return
    val progress = if (ui.durationMs > 0) (positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
    val err = ui.playbackError
    Column(
        modifier
            .fillMaxWidth()
            .sonveilGlass(p, 16.dp, opaque = true)
            .onSizeChanged { onHeightChanged(it.height) },
    ) {
        val seek = p.seekColors
        val seekSpec = SonveilMotion.emphasized<androidx.compose.ui.graphics.Color>(SonveilMotion.DurationPaletteMs)
        val seekActive by animateColorAsState(seek.active, seekSpec, label = "mini-active")
        val seekInactive by animateColorAsState(seek.inactive, seekSpec, label = "mini-inactive")
        Box(Modifier.fillMaxWidth().height(3.dp).background(seekInactive)) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(seekActive),
            )
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = SonveilSize.MiniPlayer - 3.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumCoverArt(
                song.albumId,
                Modifier.size(44.dp),
                song.title,
                corner = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    err ?: song.artist.orEmpty(),
                    color = p.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
            }
            if (err != null) {
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = SonveilSize.Action)) { Text("Retry") }
            }
            PlaybackStatus(ui, compact = true)
            IconButton(onClick = if (err != null) onRetry else onPlayPause) {
                Icon(
                    if (ui.showPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (err != null) "Retry" else if (ui.showPause) "Pause" else "Play",
                    tint = p.onBackground,
                )
            }
        }
    }
}

@Composable
internal fun ControlsDeck(ui: PlayerUiState) {
    val player = LocalPlayer.current
    val p = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val playSize = (maxWidth - SonveilSize.Action * 4).coerceIn(SonveilSize.Action, 82.dp)
        val playInteraction = remember { MutableInteractionSource() }
        val playPressed by playInteraction.collectIsPressedAsState()
        val playFocused by playInteraction.collectIsFocusedAsState()
        val playScale by animateFloatAsState(
            targetValue = if (playPressed) 0.92f else 1f,
            animationSpec = SonveilMotion.standard(SonveilMotion.DurationPressMs),
            label = "play-press",
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { player.toggleShuffle() }, modifier = Modifier.semantics {
                stateDescription = if (ui.shuffle) "Shuffle on" else "Shuffle off"
            }) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (ui.shuffle) p.primary else p.textSecondary,
                    modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { player.previous() }) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = p.onBackground, modifier = Modifier.size(42.dp))
            }
            Box(Modifier.size(playSize).shadow(8.dp, CircleShape).clip(CircleShape).background(p.playButton)
                .then(if (playFocused) Modifier.border(2.dp, p.focusRing, CircleShape) else Modifier)
                .clickable(interactionSource = playInteraction, indication = ripple(), role = Role.Button,
                    onClick = { player.playPause() }), contentAlignment = Alignment.Center) {
                // Animate the icon, not the allocated touch/focus bounds.
                Icon(if (ui.showPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ui.showPause) "Pause" else "Play", tint = p.onPlayButton,
                    modifier = Modifier.size(40.dp).scale(playScale))
            }
            IconButton(onClick = { player.next() }) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = p.onBackground, modifier = Modifier.size(42.dp))
            }
            IconButton(onClick = { player.toggleRepeat() }, modifier = Modifier.semantics {
                stateDescription = when (ui.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat one song"
                    Player.REPEAT_MODE_ALL -> "Repeat all songs"
                    else -> "Repeat off"
                }
            }) {
                Icon(if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat", tint = if (ui.repeatMode != Player.REPEAT_MODE_OFF) p.primary else p.textSecondary,
                    modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** Delay short stalls so normal track starts do not flash a loading indicator. */
@Composable
internal fun PlaybackStatus(ui: PlayerUiState, compact: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(ui.isBuffering) {
        visible = false
        if (ui.isBuffering) { delay(400); visible = true }
    }
    if (visible && ui.isBuffering) {
        if (compact) androidx.compose.material3.CircularProgressIndicator(
            Modifier.size(20.dp).semantics { contentDescription = "Buffering"; liveRegion = LiveRegionMode.Polite },
            strokeWidth = 2.dp)
        else Text("Buffering…", color = LocalPalette.current.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite })
    }
}
