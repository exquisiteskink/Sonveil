package app.sonveil.music.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.player.EqFilter
import app.sonveil.music.data.player.EqPresets
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.seekColors
import app.sonveil.music.ui.theme.sonveilSliderColors
import kotlin.math.log10
import kotlin.math.pow

private val GainRange = -12f..12f

@Composable
fun GraphicEqBank(
    preamp: Float,
    gains: FloatArray,
    onPreamp: (Float) -> Unit,
    onBand: (Int, Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(228.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        VerticalDbFader(
            label = "Pre",
            value = preamp,
            valueRange = -24f..12f,
            onValueChange = onPreamp,
            modifier = Modifier.weight(1.1f),
        )
        EqPresets.BANDS_HZ.forEachIndexed { index, hz ->
            VerticalDbFader(
                label = bandLabel(hz),
                value = gains.getOrElse(index) { 0f },
                valueRange = GainRange,
                onValueChange = { onBand(index, it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ParametricBandEditor(
    filter: EqFilter,
    index: Int,
    onChange: (EqFilter) -> Unit,
    onRemove: () -> Unit,
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Band ${index + 1}",
                color = p.onBackground,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove) { Text("Remove") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("PK" to "Peak", "LS" to "Low shelf", "HS" to "High shelf").forEach { (type, label) ->
                val selected = filter.type == type
                Text(
                    label,
                    color = if (selected) p.onPrimary else p.onBackground,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(
                            if (selected) p.primary else p.onBackground.copy(alpha = 0.12f),
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { onChange(filter.copy(type = type)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        LogHzSlider(
            hz = filter.fc,
            onHz = { onChange(filter.copy(fc = it)) },
        )
        DbSlider(
            title = "Gain",
            value = filter.gainDb,
            valueRange = GainRange,
            onValue = { onChange(filter.copy(gainDb = it)) },
        )
        DbSlider(
            title = "Q",
            value = filter.q,
            valueRange = 0.3f..8f,
            onValue = { onChange(filter.copy(q = it)) },
            format = { "%.2f".format(it) },
        )
    }
}

@Composable
private fun LogHzSlider(hz: Float, onHz: (Float) -> Unit) {
    val p = LocalPalette.current
    val min = log10(20f)
    val max = log10(20_000f)
    val position = log10(hz.coerceIn(20f, 20_000f)).coerceIn(min, max)
    Column {
        Text(
            "Frequency  ${formatHz(hz)}",
            color = p.onBackground.copy(alpha = 0.75f),
            fontSize = 12.sp,
        )
        Slider(
            value = position,
            onValueChange = { onHz(10f.pow(it)) },
            valueRange = min..max,
            colors = sonveilSliderColors(),
        )
    }
}

@Composable
private fun DbSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    format: (Float) -> String = { signedDb(it) },
) {
    val p = LocalPalette.current
    Column {
        Text(
            "$title  ${format(value)}",
            color = p.onBackground.copy(alpha = 0.75f),
            fontSize = 12.sp,
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValue,
            valueRange = valueRange,
            colors = sonveilSliderColors(),
        )
    }
}

@Composable
private fun VerticalDbFader(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val shown = value.coerceIn(valueRange.start, valueRange.endInclusive)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            signedDb(shown),
            color = p.onBackground.copy(alpha = 0.7f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .width(22.dp)
                .weight(1f)
                .pointerInput(valueRange) {
                    fun at(y: Float): Float {
                        val span = valueRange.endInclusive - valueRange.start
                        val t = (1f - y / size.height.toFloat()).coerceIn(0f, 1f)
                        return valueRange.start + t * span
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        onValueChange(at(down.position.y))
                        drag(down.id) { change ->
                            if (change.positionChange() != Offset.Zero) change.consume()
                            onValueChange(at(change.position.y))
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxHeight().fillMaxWidth()) {
                val span = valueRange.endInclusive - valueRange.start
                val t = ((shown - valueRange.start) / span).coerceIn(0f, 1f)
                val thumbY = (1f - t) * size.height
                val trackLeft = size.width * 0.38f
                val trackWidth = size.width * 0.24f
                drawRoundRect(
                    color = p.seekColors.inactive,
                    topLeft = Offset(trackLeft, 0f),
                    size = Size(trackWidth, size.height),
                    cornerRadius = CornerRadius(trackWidth, trackWidth),
                )
                drawCircle(
                    color = p.primary,
                    radius = size.width * 0.42f,
                    center = Offset(size.width / 2f, thumbY.coerceIn(size.width * 0.42f, size.height - size.width * 0.42f)),
                )
            }
        }
        Text(
            label,
            color = p.onBackground,
            fontSize = 9.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun bandLabel(hz: Int): String = when {
    hz >= 1000 && hz % 1000 == 0 -> "${hz / 1000}k"
    hz >= 1000 -> "${hz / 1000f}k"
    else -> hz.toString()
}

private fun signedDb(value: Float): String =
    (if (value > 0f) "+" else "") + "%.1f".format(value)

fun formatHz(hz: Float): String = when {
    hz >= 1000f -> "${"%.1f".format(hz / 1000f)} kHz"
    else -> "${hz.toInt()} Hz"
}
