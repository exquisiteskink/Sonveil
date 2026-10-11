package app.sonveil.music.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.splineBasedDecay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import app.sonveil.music.ui.theme.AuralisMotion
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer
import app.sonveil.music.ui.theme.LocalPlayerState
import app.sonveil.music.ui.theme.UltraBlurBackground
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class PlayerSheetValue { Collapsed, Player, Queue }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingHost(
    sheet: MutableFloatState,
    onArtist: (String) -> Unit,
    bottomNavVisible: Boolean,
) {
    val player = LocalPlayer.current
    val playerState = LocalPlayerState.current
    val ui by remember(playerState) {
        derivedStateOf { playerState.value.copy(positionMs = 0L) }
    }
    val position = remember(playerState) {
        derivedStateOf { playerState.value.positionMs }
    }
    if (ui.current == null) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var queueComposed by remember { mutableStateOf(false) }
    val sheetState = remember(density) {
        AnchoredDraggableState(
            initialValue = PlayerSheetValue.Collapsed,
            positionalThreshold = { distance -> distance * 0.45f },
            velocityThreshold = { with(density) { 900.dp.toPx() } },
            // Softer settle than MediumLow — less abrupt mini↔NP landings.
            snapAnimationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow),
            decayAnimationSpec = splineBasedDecay(density),
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val anchors = remember(heightPx) {
            DraggableAnchors {
                PlayerSheetValue.Collapsed at heightPx
                PlayerSheetValue.Player at 0f
                PlayerSheetValue.Queue at -heightPx
            }
        }
        SideEffect { sheetState.updateAnchors(anchors) }

        LaunchedEffect(sheetState, heightPx) {
            snapshotFlow { sheetState.offset }
                .collect { offset ->
                    if (offset.isFinite()) {
                        sheet.floatValue = (1f - offset / heightPx).coerceIn(0f, 1f)
                        if (offset < 0f) queueComposed = true
                    }
                }
        }
        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.settledValue }
                .distinctUntilChanged()
                .collect { settled ->
                    queueComposed = settled != PlayerSheetValue.Collapsed
                }
        }

        val handlesBack by remember(sheetState, heightPx) {
            derivedStateOf { sheetState.offset.isFinite() && sheetState.offset < heightPx * 0.8f }
        }
        BackHandler(enabled = handlesBack) {
            val target = if (sheetState.offset < -heightPx * 0.35f) {
                PlayerSheetValue.Player
            } else {
                PlayerSheetValue.Collapsed
            }
            scope.launch { sheetState.animateTo(target) }
        }

        val queueDismiss = remember(sheetState) {
            object : NestedScrollConnection {
                var downwardDragPx = 0f

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y > 0f && sheetState.offset < 0f) {
                        // Queue can close as far as Player, but this gesture must
                        // never start collapsing the Now Playing screen.
                        val consumed = sheetState.dispatchRawDelta(minOf(available.y, -sheetState.offset))
                        downwardDragPx += consumed.coerceAtLeast(0f)
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    val shouldClose = downwardDragPx > with(density) { 48.dp.toPx() } && available.y >= 0f
                    downwardDragPx = 0f
                    if (shouldClose || (sheetState.offset >= 0f && sheetState.settledValue == PlayerSheetValue.Queue)) {
                        sheetState.animateTo(PlayerSheetValue.Player)
                        return Velocity(0f, available.y)
                    }
                    if (sheetState.offset < 0f) {
                        val consumedY = sheetState.settle(available.y)
                        return Velocity(0f, consumedY)
                    }
                    return Velocity.Zero
                }
            }
        }

        PositionAwareMiniBar(
            ui = ui,
            position = position,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = if (bottomNavVisible) 64.dp else 12.dp)
                .navigationBarsPadding()
                .graphicsLayer {
                    // Fade/slide mini away as NP rises — avoids hard cut under the sheet.
                    val progress = sheet.floatValue
                    alpha = (1f - progress * 1.25f).coerceIn(0f, 1f)
                    translationY = 10.dp.toPx() * progress
                }
                .anchoredDraggable(sheetState, Orientation.Vertical)
                .clickable { scope.launch { sheetState.animateTo(PlayerSheetValue.Player) } },
            onPlayPause = { player.playPause() },
            onRetry = { player.retryPlayback() },
        )

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val offset = sheetState.offset.takeIf { it.isFinite() } ?: heightPx
                    translationY = if (sheetState.settledValue == PlayerSheetValue.Queue) 0f else offset.coerceAtLeast(0f)
                }
                .anchoredDraggable(sheetState, Orientation.Vertical, enabled = sheetState.settledValue != PlayerSheetValue.Queue),
        ) {
            UltraBlurBackground(Modifier.fillMaxSize())
            PositionAwareNowPlayingPage(
                ui = ui,
                position = position,
                onArtist = {
                    onArtist(it)
                    scope.launch { sheetState.animateTo(PlayerSheetValue.Collapsed) }
                },
                onClose = { scope.launch { sheetState.animateTo(PlayerSheetValue.Collapsed) } },
                onOpenQueue = {
                    queueComposed = true
                    scope.launch {
                        withFrameNanos { }
                        sheetState.animateTo(PlayerSheetValue.Queue)
                    }
                },
            )
            if (queueComposed) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val offset = sheetState.offset.takeIf { it.isFinite() } ?: 0f
                            translationY = heightPx + offset.coerceAtMost(0f)
                        }
                        .nestedScroll(queueDismiss),
                ) {
                    UltraBlurBackground(Modifier.fillMaxSize())
                    QueuePage(
                        ui = ui,
                        onClose = { scope.launch { sheetState.animateTo(PlayerSheetValue.Player) } },
                        headerModifier = Modifier.anchoredDraggable(sheetState, Orientation.Vertical),
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionAwareNowPlayingPage(
    ui: PlayerUiState,
    position: State<Long>,
    onArtist: (String) -> Unit,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    NowPlayingPage(
        ui = ui,
        positionMs = position.value,
        onArtist = onArtist,
        onClose = onClose,
        onOpenQueue = onOpenQueue,
    )
}

@Composable
private fun NowPlayingPage(
    ui: PlayerUiState,
    positionMs: Long,
    onArtist: (String) -> Unit,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val player = LocalPlayer.current
    val p = LocalPalette.current
    val song = ui.current ?: return
    var showLyrics by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        // Plexamp-like: large art → seek → details/meta → controls (tight).
        val artSize = minOf(maxWidth * 0.90f, maxHeight * 0.42f)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = p.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Crossfade(
                targetState = showLyrics,
                animationSpec = AuralisMotion.emphasized(AuralisMotion.DurationArtMs),
                label = "np-lyrics",
                modifier = Modifier.size(artSize),
            ) { lyricsMode ->
                if (lyricsMode) {
                    LyricsPane(
                        lyrics = ui.lyrics,
                        positionMs = positionMs,
                        modifier = Modifier.fillMaxSize(),
                        onClose = { showLyrics = false },
                    )
                } else {
                    AlbumCoverArt(
                        albumId = song.albumId,
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(22.dp, RoundedCornerShape(14.dp))
                            .clickable { showLyrics = true },
                        contentDescription = song.title,
                        corner = 14.dp,
                    )
                }
            }

            // Breathing room under art, then seek — Plexamp-like stack.
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatDurationMs(positionMs),
                    color = p.onBackground.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(40.dp),
                )
                FlatSeekBar(
                    positionMs = positionMs,
                    durationMs = ui.durationMs,
                    onSeek = { ms -> player.seek(ms) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    formatDurationMs(ui.durationMs),
                    color = p.onBackground.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(40.dp),
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                song.artist.orEmpty(),
                color = p.onBackground.copy(alpha = 0.92f),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = song.artistId != null) {
                    song.artistId?.let(onArtist)
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                song.title,
                color = p.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            song.album?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    color = p.onBackground.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ui.playbackError?.let { err ->
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        err,
                        color = p.primary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        "Retry",
                        modifier = Modifier.clickable { player.retryPlayback() },
                        color = p.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            MetaRow(
                codec = song.codecLabel,
                bitDepth = song.bitDepth,
                sampleRate = song.sampleRateLabel,
                favorite = ui.isFavorite(song),
                lyricsOpen = showLyrics,
                onToggleFavorite = { player.toggleFavorite(song) },
                onToggleLyrics = { showLyrics = !showLyrics },
            )

            // Tight gap: MetaRow → ControlsDeck (no weight gap).
            Spacer(Modifier.height(10.dp))
            ControlsDeck(ui)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onOpenQueue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Up next · ${ui.upNext.size} songs",
                    color = p.onBackground.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
            }
            // Remaining flex only after controls so seek/details stay under art.
            Spacer(Modifier.weight(1f))
        }
    }
}
