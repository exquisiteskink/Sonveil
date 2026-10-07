package app.sonveil.music.data.player

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin color math for the album-art color scheme (no Android / Compose types,
 * so it runs in plain JVM unit tests).
 *
 * Colors are ARGB ints. "Tone" is CIELAB L* (0 = black, 100 = white), the same idea as
 * Material's HCT tone: two colors whose tones differ enough always contrast, whatever
 * their hue. Hue and chroma come from CIELCh.
 */
object ArtColorMath {
    private const val E = 216.0 / 24389.0
    private const val K = 24389.0 / 27.0
    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883

    /** Below this CIELCh chroma, a color reads as grey and its hue is meaningless. */
    const val ACHROMATIC_CHROMA = 12.0

    data class Lch(val l: Double, val c: Double, val h: Double)

    fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun withAlpha(color: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (color and 0x00FFFFFF)

    private fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun delinear(v: Double): Int {
        val c = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1.0 / 2.4) - 0.055
        return (c * 255.0).roundToInt().coerceIn(0, 255)
    }

    /** WCAG 2.x relative luminance, 0..1. Alpha is ignored (colors are treated as opaque). */
    fun luminance(color: Int): Double {
        val r = linear((color shr 16) and 0xFF)
        val g = linear((color shr 8) and 0xFF)
        val b = linear(color and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** WCAG contrast ratio, 1..21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun f(t: Double) = if (t > E) cbrt(t) else (K * t + 16.0) / 116.0
    private fun fInv(t: Double): Double {
        val t3 = t * t * t
        return if (t3 > E) t3 else (116.0 * t - 16.0) / K
    }

    fun toLch(color: Int): Lch {
        val r = linear((color shr 16) and 0xFF)
        val g = linear((color shr 8) and 0xFF)
        val b = linear(color and 0xFF)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / XN
        val y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / YN
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / ZN
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        val l = 116.0 * fy - 16.0
        val aa = 500.0 * (fx - fy)
        val bb = 200.0 * (fy - fz)
        var h = Math.toDegrees(atan2(bb, aa))
        if (h < 0) h += 360.0
        return Lch(l, hypot(aa, bb), h)
    }

    fun tone(color: Int): Double = toLch(color).l

    /** Returns linear RGB, or null when outside the sRGB gamut. */
    private fun lchToLinear(l: Double, c: Double, h: Double): DoubleArray? {
        val hr = Math.toRadians(h)
        val aa = c * cos(hr)
        val bb = c * sin(hr)
        val fy = (l + 16.0) / 116.0
        val fx = fy + aa / 500.0
        val fz = fy - bb / 200.0
        val x = fInv(fx) * XN
        val y = (if (l > K * E) fy * fy * fy else l / K) * YN
        val z = fInv(fz) * ZN
        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val b = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        val eps = 1e-4
        if (r < -eps || r > 1 + eps || g < -eps || g > 1 + eps || b < -eps || b > 1 + eps) return null
        return doubleArrayOf(r.coerceIn(0.0, 1.0), g.coerceIn(0.0, 1.0), b.coerceIn(0.0, 1.0))
    }

    /**
     * Color at [tone] with the given hue, keeping as much of [chroma] as sRGB allows
     * (chroma is reduced, never tone or hue, so contrast decisions made on tone hold).
     */
    fun fromLch(tone: Double, chroma: Double, hue: Double): Int {
        val l = tone.coerceIn(0.0, 100.0)
        lchToLinear(l, chroma, hue)?.let { return toArgb(it) }
        var lo = 0.0
        var hi = chroma
        var best = lchToLinear(l, 0.0, hue) ?: doubleArrayOf(l / 100.0, l / 100.0, l / 100.0)
        repeat(24) {
            val mid = (lo + hi) / 2.0
            val rgb = lchToLinear(l, mid, hue)
            if (rgb != null) {
                best = rgb
                lo = mid
            } else {
                hi = mid
            }
        }
        return toArgb(best)
    }

    private fun toArgb(rgb: DoubleArray): Int = argb(delinear(rgb[0]), delinear(rgb[1]), delinear(rgb[2]))

    fun isChromatic(color: Int): Boolean = toLch(color).c >= ACHROMATIC_CHROMA

    /**
     * Picks the color that best represents the artwork: favors colors that are both
     * common (population) and colorful (chroma). Near-grey art yields its dominant grey,
     * which produces a neutral (but still art-matched) scheme. Null when there is nothing.
     */
    fun pickSeed(swatches: List<Pair<Int, Int>>): Int? {
        val usable = swatches.filter { it.second > 0 }
        if (usable.isEmpty()) return null
        val total = usable.sumOf { it.second }.toDouble()
        val chromatic = usable.filter { toLch(it.first).c >= ACHROMATIC_CHROMA }
        if (chromatic.isEmpty()) return usable.maxByOrNull { it.second }?.first
        return chromatic.maxByOrNull { (color, population) ->
            val lch = toLch(color)
            val proportion = population / total
            // Very dark / very light swatches look muddy as accents.
            val tonePenalty = if (lch.l < 15 || lch.l > 92) 0.5 else 1.0
            sqrt(proportion) * min(lch.c, 70.0) * tonePenalty
        }?.first
    }

    /**
     * Steps [startTone] toward higher contrast (lighter on dark UIs, darker on light UIs)
     * until [ok] holds. If no tone satisfies it, the most contrasting tone is returned.
     */
    fun solveTone(
        startTone: Double,
        lighter: Boolean,
        ok: (tone: Double) -> Boolean,
    ): Double {
        var t = startTone.coerceIn(0.0, 100.0)
        val step = if (lighter) 1.0 else -1.0
        while (t in 0.0..100.0) {
            if (ok(t)) return t
            t += step
        }
        return if (lighter) 100.0 else 0.0
    }

    fun minContrast(color: Int, against: List<Int>): Double =
        if (against.isEmpty()) 21.0 else against.minOf { contrast(color, it) }
}

/**
 * Full Material 3 color roles derived from one album-art seed color.
 * Built off the main thread in [PaletteExtractor]; consumed by `AuralisTheme`.
 */
data class ArtScheme(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val inversePrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val surfaceDim: Int,
    val surfaceBright: Int,
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    /** Played part of the seek bar / mini progress and the thumb. Same as [primary]. */
    val seekActive: Int,
    /** Unplayed part of the seek bar (translucent, hue-tinted). */
    val seekInactive: Int,
) {
    companion object {
        /** Non-text UI (seek track, thumb, switch track) — WCAG 1.4.11. */
        const val MIN_UI_CONTRAST = 3.0
        /** Text on a filled container (button labels, on-colors) — WCAG 1.4.3. */
        const val MIN_TEXT_CONTRAST = 4.5

        /**
         * @param seed album-art seed color (or the app's static brand color as fallback).
         * @param dark whether the UI is dark.
         * @param backdrops colors the accent is drawn over (page blurs, glass cards); the
         *   accent is moved in tone until it reaches [MIN_UI_CONTRAST] against all of them.
         * @param textBackdrops surfaces where the accent is used as *text* (e.g. TextButton
         *   labels on Settings cards); the accent must reach [MIN_TEXT_CONTRAST] on them.
         */
        fun from(
            seed: Int,
            dark: Boolean,
            backdrops: List<Int>,
            textBackdrops: List<Int> = emptyList(),
        ): ArtScheme {
            val m = ArtColorMath
            val s = m.toLch(seed)
            val chromatic = s.c >= ArtColorMath.ACHROMATIC_CHROMA
            val hue = s.h
            // Keep the art's own colorfulness, but make faint colors clearly colored and
            // keep grey art neutral instead of inventing a hue.
            val pc = if (chromatic) s.c.coerceIn(36.0, 90.0) else min(s.c, 6.0)
            val sc = if (chromatic) max(pc / 3.0, 10.0) else min(s.c, 4.0)
            val tc = if (chromatic) max(pc / 2.0, 16.0) else min(s.c, 4.0)
            val nc = if (chromatic) 6.0 else min(s.c, 2.0)
            val nvc = if (chromatic) 10.0 else min(s.c, 3.0)
            fun p(t: Double) = m.fromLch(t, pc, hue)
            fun sec(t: Double) = m.fromLch(t, sc, hue)
            fun ter(t: Double) = m.fromLch(t, tc, (hue + 60.0) % 360.0)
            fun n(t: Double) = m.fromLch(t, nc, hue)
            fun nv(t: Double) = m.fromLch(t, nvc, hue)

            // Primary: start from the art color's own tone (so it still looks like the art),
            // bounded to a usable band, then push it until it is readable on every backdrop
            // and its on-color can carry text.
            val start = if (dark) s.l.coerceIn(65.0, 85.0) else s.l.coerceIn(35.0, 50.0)
            val onTone = if (dark) 10.0 else 100.0
            val primaryTone = m.solveTone(start, lighter = dark) { t ->
                val c = p(t)
                m.minContrast(c, backdrops) >= MIN_UI_CONTRAST &&
                    m.minContrast(c, textBackdrops) >= MIN_TEXT_CONTRAST &&
                    m.contrast(c, p(onTone)) >= MIN_TEXT_CONTRAST
            }
            val primary = p(primaryTone)
            val onPrimary = bestOn(primary, p(onTone), p(if (dark) 100.0 else 10.0))

            val secondaryTone = m.solveTone(if (dark) 80.0 else 40.0, lighter = dark) { t ->
                m.minContrast(sec(t), backdrops) >= MIN_UI_CONTRAST
            }
            val tertiaryTone = m.solveTone(if (dark) 80.0 else 40.0, lighter = dark) { t ->
                m.minContrast(ter(t), backdrops) >= MIN_UI_CONTRAST
            }
            val secondary = sec(secondaryTone)
            val tertiary = ter(tertiaryTone)

            return ArtScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = p(if (dark) 30.0 else 90.0),
                onPrimaryContainer = p(if (dark) 90.0 else 10.0),
                inversePrimary = p(if (dark) 40.0 else 80.0),
                secondary = secondary,
                onSecondary = bestOn(secondary, sec(if (dark) 10.0 else 100.0), sec(if (dark) 100.0 else 10.0)),
                secondaryContainer = sec(if (dark) 30.0 else 90.0),
                onSecondaryContainer = sec(if (dark) 90.0 else 10.0),
                tertiary = tertiary,
                onTertiary = bestOn(tertiary, ter(if (dark) 10.0 else 100.0), ter(if (dark) 100.0 else 10.0)),
                tertiaryContainer = ter(if (dark) 30.0 else 90.0),
                onTertiaryContainer = ter(if (dark) 90.0 else 10.0),
                onSurfaceVariant = nv(if (dark) 80.0 else 30.0),
                outline = nv(if (dark) 60.0 else 50.0),
                outlineVariant = nv(if (dark) 30.0 else 80.0),
                inverseSurface = n(if (dark) 90.0 else 20.0),
                inverseOnSurface = n(if (dark) 20.0 else 95.0),
                surfaceDim = n(if (dark) 6.0 else 87.0),
                surfaceBright = n(if (dark) 24.0 else 98.0),
                surfaceContainerLowest = n(if (dark) 4.0 else 100.0),
                surfaceContainerLow = n(if (dark) 10.0 else 96.0),
                surfaceContainer = n(if (dark) 12.0 else 94.0),
                surfaceContainerHigh = n(if (dark) 17.0 else 92.0),
                surfaceContainerHighest = n(if (dark) 22.0 else 90.0),
                seekActive = primary,
                seekInactive = m.withAlpha(m.fromLch(if (dark) 88.0 else 22.0, min(pc, 16.0), hue), if (dark) 0.32f else 0.26f),
            )
        }

        private fun bestOn(container: Int, preferred: Int, alternative: Int): Int {
            val a = ArtColorMath.contrast(container, preferred)
            if (a >= MIN_TEXT_CONTRAST) return preferred
            val b = ArtColorMath.contrast(container, alternative)
            return if (b > a) alternative else preferred
        }
    }
}
