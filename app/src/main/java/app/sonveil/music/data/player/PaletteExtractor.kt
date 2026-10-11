package app.sonveil.music.data.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.min

/** Signature Plexamp waveform / progress gold. */
val WaveformGold = Color(0xFFE8A317)

data class SonveilPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onBackground: Color,
    val onSurface: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val outline: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val scrim: Color,
    val playButton: Color,
    val onPlayButton: Color,
    val blurA: Color,
    val blurB: Color,
    val blurC: Color,
    /**
     * Full Material 3 roles derived from one seed (album art, or [WaveformGold] for the
     * static app scheme). [primary]/[onPrimary] are taken from here so the palette and
     * MaterialTheme can never disagree.
     */
    val scheme: ArtScheme,
    /** True when colors came from the current track's artwork (false = static fallback). */
    val fromArt: Boolean = false,
) {
    companion object {
        private val DARK_DEFAULT by lazy { buildDarkDefault() }
        private val LIGHT_DEFAULT by lazy { buildLightDefault() }

        fun darkDefault(): SonveilPalette = DARK_DEFAULT

        fun lightDefault(): SonveilPalette = LIGHT_DEFAULT

        /**
         * Single place that turns a set of art-derived surfaces plus a seed into a palette:
         * accent roles come from the tonal [ArtScheme], contrast-checked against the
         * surfaces they are drawn on.
         */
        internal fun withScheme(base: SonveilPalette, seed: Int, fromArt: Boolean): SonveilPalette {
            val backdrops = listOf(base.background, base.surface, base.surfaceHigh, base.blurA, base.blurB, base.blurC)
                .map { it.toArgb() }
            val textBackdrops = listOf(base.surface, base.surfaceHigh).map { it.toArgb() }
            val scheme = ArtScheme.from(seed, base.isDark, backdrops, textBackdrops)
            return base.copy(
                primary = Color(scheme.primary),
                onPrimary = Color(scheme.onPrimary),
                scheme = scheme,
                fromArt = fromArt,
            )
        }

        private val PlaceholderScheme: ArtScheme by lazy {
            ArtScheme.from(WaveformGold.toArgb(), dark = true, backdrops = emptyList())
        }

        private fun buildDarkDefault() = withScheme(SonveilPalette(
            isDark = true,
            background = Color(0xFF070B13),
            surface = Color(0xFF101B2A),
            surfaceHigh = Color(0xFF1C3044),
            onBackground = Color(0xFFFFFFFF),
            onSurface = Color(0xFFE6E6E6),
            primary = WaveformGold,
            onPrimary = Color(0xFF1A1200),
            secondary = Color(0xFF8A8A8A),
            outline = Color(0xFF2C4358),
            gradientTop = Color(0xFF12243A),
            gradientBottom = Color(0xFF070B13),
            scrim = Color(0xFF070B13),
            playButton = Color(0xE61B2A3A),
            onPlayButton = Color.White,
            blurA = Color(0xFF173650),
            blurB = Color(0xFF553314),
            blurC = Color(0xFF102036),
            scheme = PlaceholderScheme,
        ), WaveformGold.toArgb(), fromArt = false)

        private fun buildLightDefault() = withScheme(SonveilPalette(
            isDark = false,
            background = Color(0xFFE2E2E2),
            surface = Color(0xFFEAEAEA),
            surfaceHigh = Color(0xFFD8D8D8),
            onBackground = Color(0xFF141414),
            onSurface = Color(0xFF1A1A1A),
            primary = WaveformGold,
            onPrimary = Color(0xFF1A1200),
            secondary = Color(0xFF6A6A6A),
            outline = Color(0xFFC4C4C4),
            gradientTop = Color(0xFFD8D8D8),
            gradientBottom = Color(0xFFE6E6E6),
            scrim = Color(0xFFE2E2E2),
            playButton = Color(0xFFC2C2C2),
            onPlayButton = Color(0xFF111111),
            blurA = Color(0xFFD4D4D4),
            blurB = Color(0xFFDEDEDE),
            blurC = Color(0xFFE8E8E8),
            scheme = PlaceholderScheme,
        ), WaveformGold.toArgb(), fromArt = false)
    }
}

object PaletteExtractor {
    fun from(bitmap: Bitmap, preferDark: Boolean): SonveilPalette {
        val palette = Palette.from(bitmap).clearFilters().generate()
        val vibrant = palette.vibrantSwatch
        val darkVibrant = palette.darkVibrantSwatch
        val muted = palette.mutedSwatch
        val darkMuted = palette.darkMutedSwatch
        val lightMuted = palette.lightMutedSwatch
        val lightVibrant = palette.lightVibrantSwatch
        val dominant = palette.dominantSwatch
        // One seed for every accent (seek bar, buttons, switches...), chosen from the whole
        // artwork rather than a single fixed swatch.
        val seed = ArtColorMath.pickSeed(palette.swatches.map { it.rgb to it.population })
            ?: dominant?.rgb
            ?: WaveformGold.toArgb()

        val base = if (preferDark) {
            val a = (darkVibrant ?: vibrant ?: dominant)?.rgb.toColor(Color(0xFF3A2A22)).asBlur(0.22f, 0.42f, 0.55f)
            val b = (muted ?: darkMuted ?: dominant)?.rgb.toColor(Color(0xFF2A2420)).asBlur(0.16f, 0.32f, 0.45f)
            val c = (darkMuted ?: dominant ?: muted)?.rgb.toColor(Color(0xFF1A1614)).asBlur(0.10f, 0.24f, 0.40f)
            val mini = a.asBlur(0.18f, 0.30f, 0.40f)
            SonveilPalette(
                isDark = true,
                background = Color(0xFF000000),
                surface = mini,
                surfaceHigh = mini.lighten(0.08f),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFE8E8E8),
                primary = Color(seed),
                onPrimary = Color(0xFF1A1200),
                secondary = b,
                outline = Color.White.copy(alpha = 0.10f),
                gradientTop = a,
                gradientBottom = c,
                scrim = Color(0xFF000000),
                playButton = Color(0xE6141414),
                onPlayButton = Color.White,
                blurA = a,
                blurB = b,
                blurC = c,
                scheme = SonveilPalette.darkDefault().scheme,
            )
        } else {
            val a = (vibrant ?: lightVibrant ?: dominant)?.rgb.toColor(Color(0xFFC8C0B8)).asLightBlur(0.62f, 0.78f, 0.52f)
            val b = (muted ?: lightMuted ?: dominant)?.rgb.toColor(Color(0xFFC4C4C4)).asLightBlur(0.68f, 0.82f, 0.40f)
            val c = (lightMuted ?: muted ?: dominant)?.rgb.toColor(Color(0xFFD0D0D0)).asLightBlur(0.74f, 0.86f, 0.32f)
            val page = b.asLightBlur(0.78f, 0.86f, 0.22f)
            val mini = a.asLightBlur(0.70f, 0.80f, 0.36f)
            SonveilPalette(
                isDark = false,
                background = page,
                surface = mini,
                surfaceHigh = a.asLightBlur(0.66f, 0.76f, 0.30f),
                onBackground = Color(0xFF141414),
                onSurface = Color(0xFF1A1A1A),
                primary = Color(seed),
                onPrimary = Color.White,
                secondary = b,
                outline = Color.Black.copy(alpha = 0.10f),
                gradientTop = a,
                gradientBottom = c,
                scrim = page,
                playButton = Color(0xFFBDBDBD),
                onPlayButton = Color(0xFF111111),
                blurA = a,
                blurB = b,
                blurC = c,
                scheme = SonveilPalette.darkDefault().scheme,
            )
        }
        return SonveilPalette.withScheme(base, seed, fromArt = true)
    }

    private fun Int?.toColor(fallback: Color): Color = if (this == null) fallback else Color(this)

    private fun Color.darken(amount: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        hsv[2] = max(0f, hsv[2] * (1f - amount))
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun Color.lighten(amount: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        hsv[2] = min(1f, hsv[2] + (1f - hsv[2]) * amount)
        hsv[1] = min(1f, hsv[1] * (1f - amount * 0.35f))
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun Color.asBlur(minV: Float, maxV: Float, sat: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        hsv[2] = hsv[2].coerceIn(minV, maxV)
        hsv[1] = hsv[1].coerceIn(0.12f, 0.72f) * (0.55f + sat * 0.45f)
        hsv[1] = hsv[1].coerceIn(0.12f, 0.70f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun Color.asLightBlur(minV: Float, maxV: Float, sat: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        if (hsv[1] < 0.08f) hsv[1] = 0.12f
        hsv[1] = (hsv[1] * 0.85f).coerceIn(0.16f, sat.coerceIn(0.16f, 0.62f))
        hsv[2] = hsv[2].coerceIn(minV, maxV)
        if (hsv[2] < minV) hsv[2] = minV
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

}
