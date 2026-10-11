package app.sonveil.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.sonveil.music.ui.theme.SonveilMotion
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.seekColors
import app.sonveil.music.ui.theme.UltraBlurBackground

@Composable
fun AuroraWash(modifier: Modifier = Modifier) {
    UltraBlurBackground(modifier)
}

/**
 * Expanded Now Playing seek control — plain flat scrubber only.
 * Owner / CoS rejected waveform (decorative and decode/flat-then-pop).
 * Keeps the 56.dp hit target so plexamp spacing from #9 stays intact.
 * Mini player keeps its own 3.dp progress bar in [MiniBar].
 */
@Composable
fun FlatSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val dur = durationMs.coerceAtLeast(1L)
    var preview by remember { mutableFloatStateOf(-1f) }
    val shown = if (preview >= 0f) preview else (positionMs.toFloat() / dur).coerceIn(0f, 1f)
    // Same art-derived, contrast-checked tokens as the mini player progress; no fixed colors.
    val seek = p.seekColors
    val spec = SonveilMotion.emphasized<Color>(SonveilMotion.DurationPaletteMs)
    val played = animateColorAsState(seek.active, spec, label = "seek-active")
    val thumb = animateColorAsState(seek.thumb, spec, label = "seek-thumb")
    val rest = animateColorAsState(seek.inactive, spec, label = "seek-inactive")

    fun fractionAt(x: Float, width: Float): Float = (x / width).coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(dur) {
                detectTapGestures { offset ->
                    onSeek((fractionAt(offset.x, size.width.toFloat()) * dur).toLong())
                }
            }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        preview = fractionAt(offset.x, size.width.toFloat())
                    },
                    onHorizontalDrag = { change, _ ->
                        preview = fractionAt(change.position.x, size.width.toFloat())
                    },
                    onDragEnd = {
                        if (preview >= 0f) onSeek((preview * dur).toLong())
                        preview = -1f
                    },
                    onDragCancel = { preview = -1f },
                )
            },
    ) {
        val mid = size.height / 2f
        val trackH = 3.dp.toPx()
        val splitX = size.width * shown
        drawLine(
            rest.value,
            Offset(0f, mid),
            Offset(size.width, mid),
            trackH,
            StrokeCap.Round,
        )
        if (shown > 0.001f) {
            drawLine(
                played.value,
                Offset(0f, mid),
                Offset(splitX, mid),
                trackH,
                StrokeCap.Round,
            )
        }
        drawCircle(thumb.value, radius = 5.dp.toPx(), center = Offset(splitX, mid))
    }
}
