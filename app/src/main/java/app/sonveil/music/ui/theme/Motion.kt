package app.sonveil.music.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * Shared motion tokens — Material-ish decelerate, longer than Compose defaults
 * so nav / sheet / palette feel less snappy.
 */
object SonveilMotion {
    /** Soft landing (Material emphasized decelerate). */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Standard ease for exits / quick feedback. */
    val Standard: Easing = FastOutSlowInEasing

    const val DurationShortMs = 200
    const val DurationMediumMs = 320
    const val DurationLongMs = 420
    const val DurationPaletteMs = 900
    const val DurationArtMs = 380
    const val DurationPressMs = 160

    fun <T> fade(durationMs: Int = DurationMediumMs) =
        tween<T>(durationMillis = durationMs, easing = EmphasizedDecelerate)

    fun <T> emphasized(durationMs: Int = DurationMediumMs) =
        tween<T>(durationMillis = durationMs, easing = EmphasizedDecelerate)

    fun <T> standard(durationMs: Int = DurationShortMs) =
        tween<T>(durationMillis = durationMs, easing = Standard)
}
