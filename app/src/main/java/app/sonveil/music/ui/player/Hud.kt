package app.sonveil.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.sonveil.music.data.remote.formatDurationMs
import app.sonveil.music.ui.theme.*

@Composable
fun AuroraWash(modifier: Modifier = Modifier) = UltraBlurBackground(modifier)

/** Flat 56dp scrubber, equally operable by touch, accessibility services and keyboard. */
@Composable
fun FlatSeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val canSeek = durationMs > 0
    var preview by remember(durationMs) { mutableFloatStateOf(-1f) }
    var focused by remember { mutableStateOf(false) }
    val shown = if (preview >= 0f) preview else if (canSeek)
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val seek = p.seekColors
    val spec = SonveilMotion.emphasized<Color>(SonveilMotion.DurationPaletteMs)
    val played = animateColorAsState(seek.active, spec, label = "seek-active")
    val thumb = animateColorAsState(seek.thumb, spec, label = "seek-thumb")
    val rest = animateColorAsState(seek.inactive, spec, label = "seek-inactive")
    val latestSeek by rememberUpdatedState(onSeek)
    fun fractionAt(x: Float, width: Float) = if (width > 0) (x / width).coerceIn(0f, 1f) else 0f

    Canvas(modifier.fillMaxWidth().height(56.dp)
        .then(if (focused) Modifier.border(2.dp, p.focusRing, RoundedCornerShape(8.dp)) else Modifier)
        .semantics {
            contentDescription = "Playback position"
            stateDescription = if (canSeek) "${formatDurationMs(positionMs)} of ${formatDurationMs(durationMs)}" else "Duration unavailable"
            if (canSeek) {
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                setProgress { target -> latestSeek(seekPosition(target, durationMs)); true }
            } else disabled()
        }
        .onFocusChanged { focused = it.isFocused }
        .onKeyEvent { event ->
            if (!canSeek || event.type != KeyEventType.KeyDown) false
            else when (event.key) {
                Key.DirectionLeft -> { latestSeek((positionMs - 5_000).coerceAtLeast(0)); true }
                Key.DirectionRight -> { latestSeek((positionMs + 5_000).coerceAtMost(durationMs)); true }
                Key.MoveHome -> { latestSeek(0); true }
                Key.MoveEnd -> { latestSeek(durationMs); true }
                else -> false
            }
        }
        .focusable(enabled = canSeek)
        .pointerInput(durationMs) {
            if (canSeek) detectTapGestures { offset ->
                latestSeek(seekPosition(fractionAt(offset.x, size.width.toFloat()), durationMs))
            }
        }
        .pointerInput(durationMs) {
            if (canSeek) detectHorizontalDragGestures(
                onDragStart = { preview = fractionAt(it.x, size.width.toFloat()) },
                onHorizontalDrag = { change, _ -> preview = fractionAt(change.position.x, size.width.toFloat()) },
                onDragEnd = {
                    if (preview >= 0) latestSeek(seekPosition(preview, durationMs))
                    preview = -1f
                },
                onDragCancel = { preview = -1f },
            )
        }) {
        val mid = size.height / 2
        val splitX = size.width * shown
        drawLine(rest.value, Offset(0f, mid), Offset(size.width, mid), 3.dp.toPx(), StrokeCap.Round)
        if (shown > .001f) drawLine(played.value, Offset(0f, mid), Offset(splitX, mid), 3.dp.toPx(), StrokeCap.Round)
        if (canSeek) drawCircle(thumb.value, 5.dp.toPx(), Offset(splitX, mid))
    }
}
