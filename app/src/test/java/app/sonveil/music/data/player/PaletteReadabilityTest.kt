package app.sonveil.music.data.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PaletteReadabilityTest {
    @Test fun sourceTextAndMetadataStayReadableAcrossArtAndCompositedSurfaces() {
        val seeds = listOf(0xFF0000FF, 0xFFFF0000, 0xFF00FF00, 0xFFFFFF00, 0xFF000000, 0xFFFFFFFF, 0xFF777777)
        for (dark in listOf(false, true)) {
            val palettes = seeds.map { seed ->
                val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                try { bitmap.eraseColor(seed.toInt()); PaletteExtractor.from(bitmap, dark) } finally { bitmap.recycle() }
            } + if (dark) SonveilPalette.darkDefault() else SonveilPalette.lightDefault()
            for (palette in palettes) {
                val endpoints = listOf(palette.background, palette.surface, palette.surfaceHigh,
                    palette.blurA, palette.blurB, palette.blurC)
                val wash = endpoints.map { Color.Black.copy(alpha = if (dark) .28f else .06f).compositeOver(it) }
                // Intermediate background/wash/glass blends, including transitions between albums.
                val surfaces = endpoints + wash + palettes.flatMap { other ->
                    listOf(.25f, .5f, .75f).map { fraction ->
                        androidx.compose.ui.graphics.lerp(palette.surface, other.surfaceHigh, fraction)
                    }
                }
                for (surface in surfaces) {
                    assertTrue("primary text dark=$dark surface=$surface",
                        ArtColorMath.contrast(palette.onBackground.toArgb(), surface.toArgb()) >= 4.5)
                    assertTrue("secondary dark=$dark surface=$surface",
                        ArtColorMath.contrast(palette.scheme.textSecondary, surface.toArgb()) >= 4.5)
                    assertTrue("tertiary dark=$dark surface=$surface",
                        ArtColorMath.contrast(palette.scheme.textTertiary, surface.toArgb()) >= 4.5)
                }
            }
        }
    }
}
