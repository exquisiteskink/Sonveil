package app.sonveil.music.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.splineBasedDecay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.sonveil.music.data.player.PlayerUiState
import app.sonveil.music.ui.components.AlbumCoverArt
import app.sonveil.music.ui.theme.SonveilMotion
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer
import app.sonveil.music.ui.theme.LocalPlayerState
import app.sonveil.music.ui.theme.UltraBlurBackground
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import app.sonveil.music.ui.theme.SonveilSize

private enum class PlayerSheetValue { Collapsed, Player, Queue }

@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun NowPlayingHost(
    sheet: MutableFloatState,
    onArtist: (String) -> Unit,
    bottomNavVisible: Boolean,
    onMiniHeightChanged: (Dp) -> Unit = {},
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
    val miniFocus = remember { FocusRequester() }
    val playerFocus = remember { FocusRequester() }
    val queueFocus = remember { FocusRequester() }
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
        val activeSurface by remember(sheetState, heightPx) { derivedStateOf {
            val offset = sheetState.offset.takeIf { it.isFinite() } ?: heightPx
            when {
                offset < -heightPx * .5f -> PlayerSheetValue.Queue
                offset < heightPx * .5f -> PlayerSheetValue.Player
                else -> PlayerSheetValue.Collapsed
            }
        } }
        LaunchedEffect(activeSurface) {
            withFrameNanos { }
            when (activeSurface) {
                PlayerSheetValue.Collapsed -> miniFocus.requestFocus()
                PlayerSheetValue.Player -> playerFocus.requestFocus()
                PlayerSheetValue.Queue -> queueFocus.requestFocus()
            }
        }
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

        // The Now Playing page contains scrollable content (metadata at large font
        // scales) that consumes vertical drags before the parent's anchoredDraggable
        // can see them. Chain any unconsumed deltas into the sheet so swiping down
        // (collapse) and swiping up (open queue) still work over the whole screen.
        val playerScrollConnection = remember(sheetState) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y == 0f || sheetState.settledValue == PlayerSheetValue.Queue) return Offset.Zero
                    val sheetConsumed = sheetState.dispatchRawDelta(available.y)
                    return Offset(0f, sheetConsumed)
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (sheetState.settledValue == PlayerSheetValue.Queue) return available
                    val settled = sheetState.settle(available.y)
                    return Velocity(0f, available.y - settled)
                }
            }
        }

        PositionAwareMiniBar(
            ui = ui,
            position = position,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp,
                    bottom = if (bottomNavVisible) SonveilSize.Navigation + SonveilSize.MiniGap else SonveilSize.MiniGap)
                .navigationBarsPadding()
                .graphicsLayer {
                    // Fade/slide mini away as NP rises — avoids hard cut under the sheet.
                    val progress = sheet.floatValue
                    alpha = (1f - progress * 1.25f).coerceIn(0f, 1f)
                    translationY = 10.dp.toPx() * progress
                }
                .then(if (activeSurface != PlayerSheetValue.Collapsed) Modifier.clearAndSetSemantics { } else Modifier)
                .focusProperties { enter = { if (activeSurface != PlayerSheetValue.Collapsed) FocusRequester.Cancel else FocusRequester.Default } }
                .focusGroup()
                .focusRequester(miniFocus)
                .anchoredDraggable(sheetState, Orientation.Vertical, enabled = activeSurface == PlayerSheetValue.Collapsed)
                .clickable(enabled = activeSurface == PlayerSheetValue.Collapsed) { scope.launch { sheetState.animateTo(PlayerSheetValue.Player) } },
            onHeightChanged = { pixels -> onMiniHeightChanged(with(density) { pixels.toDp() }) },
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
                .nestedScroll(playerScrollConnection)
                .anchoredDraggable(sheetState, Orientation.Vertical, enabled = sheetState.settledValue != PlayerSheetValue.Queue),
        ) {
            UltraBlurBackground(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize()
                .then(if (activeSurface != PlayerSheetValue.Player) Modifier.clearAndSetSemantics { } else Modifier)
                .focusProperties { enter = { if (activeSurface != PlayerSheetValue.Player) FocusRequester.Cancel else FocusRequester.Default } }
                .focusGroup()) {
            PositionAwareNowPlayingPage(
                ui = ui,
                position = position,
                onArtist = {
                    onArtist(it)
                    scope.launch { sheetState.animateTo(PlayerSheetValue.Collapsed) }
                },
                onClose = { scope.launch { sheetState.animateTo(PlayerSheetValue.Collapsed) } },
                closeModifier = Modifier.focusRequester(playerFocus),
                onOpenQueue = {
                    queueComposed = true
                    scope.launch {
                        withFrameNanos { }
                        sheetState.animateTo(PlayerSheetValue.Queue)
                    }
                },
            )
            }
            if (queueComposed) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val offset = sheetState.offset.takeIf { it.isFinite() } ?: 0f
                            translationY = heightPx + offset.coerceAtMost(0f)
                        }
                        .nestedScroll(queueDismiss)
                        .then(if (activeSurface != PlayerSheetValue.Queue) Modifier.clearAndSetSemantics { } else Modifier)
                        .focusProperties { enter = { if (activeSurface != PlayerSheetValue.Queue) FocusRequester.Cancel else FocusRequester.Default } }
                        .focusGroup(),
                ) {
                    UltraBlurBackground(Modifier.fillMaxSize())
                    QueuePage(
                        ui = ui,
                        onClose = { scope.launch { sheetState.animateTo(PlayerSheetValue.Player) } },
                        headerModifier = Modifier.anchoredDraggable(sheetState, Orientation.Vertical),
                        closeModifier = Modifier.focusRequester(queueFocus),
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
    modifier: Modifier = Modifier,
    closeModifier: Modifier = Modifier,
) {
    NowPlayingPage(
        ui = ui,
        positionMs = position.value,
        onArtist = onArtist,
        onClose = onClose,
        onOpenQueue = onOpenQueue,
        modifier = modifier,
        closeModifier = closeModifier,
    )
}

@Composable
private fun NowPlayingPage(
    ui: PlayerUiState,
    positionMs: Long,
    onArtist: (String) -> Unit,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    closeModifier: Modifier = Modifier,
) {
    val player = LocalPlayer.current
    val p = LocalPalette.current
    val song = ui.current ?: return
    var showLyrics by remember { mutableStateOf(false) }

    NowPlayingContent(
        ui = ui, positionMs = positionMs, lyricsOpen = showLyrics,
        onArtist = onArtist, onClose = onClose, onOpenQueue = onOpenQueue,
        onSeek = player::seek, onRetry = player::retryPlayback,
        onToggleFavorite = { player.toggleFavorite(song) }, onToggleLyrics = { showLyrics = !showLyrics },
        artwork = { modifier ->
            Crossfade(targetState = showLyrics,
                animationSpec = SonveilMotion.emphasized(SonveilMotion.DurationArtMs), label = "np-lyrics",
                modifier = modifier) { lyricsMode ->
                if (lyricsMode) LyricsPane(ui.lyrics, positionMs, Modifier.fillMaxSize(), { showLyrics = false })
                else AlbumCoverArt(song.albumId, Modifier.fillMaxSize().shadow(12.dp, RoundedCornerShape(14.dp))
                    .clickable { showLyrics = true }, song.title, corner = 14.dp)
            }
        },
        controls = { ControlsDeck(ui) },
        modifier = modifier,
        closeModifier = closeModifier,
    )
}
