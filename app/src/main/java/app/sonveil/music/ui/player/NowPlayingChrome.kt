package app.sonveil.music.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.splineBasedDecay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.anchoredDraggable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.sonveil.music.data.player.PlayerUiState
import app.sonveil.music.data.remote.formatDurationMs
import app.sonveil.music.ui.components.AlbumCoverArt
import app.sonveil.music.ui.components.SongRow
import app.sonveil.music.ui.theme.SonveilMotion
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer
import app.sonveil.music.ui.theme.UltraBlurBackground
import app.sonveil.music.ui.theme.seekColors
import app.sonveil.music.ui.theme.sonveilGlass
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    val mute = p.onBackground.copy(alpha = 0.45f)
    val audioFormat = listOfNotNull(
        bitDepth.takeIf { it > 0 }?.let { "$it-bit" },
        sampleRate?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!codec.isNullOrBlank()) {
            Text(codec, color = mute, fontSize = 13.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
        }
        IconButton(onClick = onToggleLyrics, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.Lyrics,
                contentDescription = if (lyricsOpen) "Hide lyrics" else "Show lyrics",
                tint = if (lyricsOpen) p.primary else mute,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                tint = if (favorite) p.primary else mute,
                modifier = Modifier.size(22.dp),
            )
        }
        if (audioFormat.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(audioFormat, color = mute, fontSize = 13.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium)
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
                    color = p.onBackground.copy(alpha = 0.55f),
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
                        color = p.onBackground.copy(alpha = if (current) 1f else 0.42f),
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
    headerModifier: Modifier = Modifier,
) {
    val player = LocalPlayer.current
    val p = LocalPalette.current
    val song = ui.current ?: return

    Column(
        Modifier
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
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Back", tint = p.onBackground)
            }
            AlbumCoverArt(song.albumId, Modifier.size(44.dp), song.title, corner = 4.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(song.artist.orEmpty(), color = p.onBackground.copy(alpha = 0.55f), maxLines = 1, fontSize = 12.sp)
            }
            IconButton(onClick = { player.playPause() }) {
                Icon(
                    if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ui.isPlaying) "Pause" else "Play",
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
                        color = p.onBackground.copy(alpha = 0.5f),
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
) {
    MiniBar(
        ui = ui,
        positionMs = position.value,
        modifier = modifier,
        onPlayPause = onPlayPause,
        onRetry = onRetry,
    )
}

@Composable
internal fun MiniBar(
    ui: PlayerUiState,
    positionMs: Long,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit = onPlayPause,
) {
    val p = LocalPalette.current
    val song = ui.current ?: return
    val progress = if (ui.durationMs > 0) (positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
    val err = ui.playbackError
    Column(
        modifier
            .fillMaxWidth()
            .sonveilGlass(p, 16.dp, opaque = true),
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
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
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
                    color = if (err != null) p.primary else p.onBackground.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
            }
            if (err != null) {
                Text(
                    "Retry",
                    modifier = Modifier
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = p.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
            IconButton(onClick = if (err != null) onRetry else onPlayPause) {
                Icon(
                    if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (err != null) "Retry" else if (ui.isPlaying) "Pause" else "Play",
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
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = { player.toggleShuffle() }) {
            Icon(
                Icons.Rounded.Shuffle,
                "Shuffle",
                tint = if (ui.shuffle) p.primary else p.onBackground.copy(alpha = 0.32f),
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(onClick = { player.previous() }) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", tint = p.onBackground, modifier = Modifier.size(42.dp))
        }
        val playInteraction = remember { MutableInteractionSource() }
        val playPressed by playInteraction.collectIsPressedAsState()
        val playScale by animateFloatAsState(
            targetValue = if (playPressed) 0.92f else 1f,
            animationSpec = SonveilMotion.standard(SonveilMotion.DurationPressMs),
            label = "play-press",
        )
        Box(
            Modifier
                .scale(playScale)
                .size(82.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(p.playButton)
                .clickable(
                    interactionSource = playInteraction,
                    indication = null,
                    onClick = { player.playPause() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (ui.isPlaying) "Pause" else "Play",
                tint = p.onPlayButton,
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = { player.next() }) {
            Icon(Icons.Rounded.SkipNext, "Next", tint = p.onBackground, modifier = Modifier.size(42.dp))
        }
        IconButton(onClick = { player.toggleRepeat() }) {
            Icon(
                if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat",
                tint = if (ui.repeatMode != Player.REPEAT_MODE_OFF) p.primary else p.onBackground.copy(alpha = 0.32f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
