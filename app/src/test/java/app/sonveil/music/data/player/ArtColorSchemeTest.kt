package app.sonveil.music.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtColorSchemeTest {
    private val m = ArtColorMath

    // Representative surfaces from AuralisPalette (static defaults + art-derived blurs).
    private val darkBackdrops = listOf(
        0xFF000000.toInt(), 0xFF101B2A.toInt(), 0xFF1C3044.toInt(),
        0xFF173650.toInt(), 0xFF553314.toInt(), 0xFF102036.toInt(), 0xFF5A4A30.toInt(),
    )
    private val darkText = listOf(0xFF101B2A.toInt(), 0xFF1C3044.toInt())
    private val lightBackdrops = listOf(
        0xFFE2E2E2.toInt(), 0xFFEAEAEA.toInt(), 0xFFD8D8D8.toInt(),
        0xFFC9B9A0.toInt(), 0xFFB8C8D8.toInt(), 0xFFE8E8E8.toInt(),
    )
    private val lightText = listOf(0xFFEAEAEA.toInt(), 0xFFD8D8D8.toInt())

    private val seeds = listOf(
        0xFFE8A317.toInt(), // app gold fallback
        0xFFD32F2F.toInt(), // red
        0xFF1565C0.toInt(), // deep blue
        0xFFFFEB3B.toInt(), // pale yellow (hard on light UIs)
        0xFF00E676.toInt(), // neon green
        0xFF6A1B9A.toInt(), // dark purple
        0xFF808080.toInt(), // grey art
        0xFF0A0A0A.toInt(), // near-black art
        0xFFF5F5F5.toInt(), // near-white art
    )

    @Test
    fun contrastMatchesWcagReferenceValues() {
        assertEquals(21.0, m.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, m.contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.0001)
        // #777777 on white is the classic ~4.48:1.
        assertEquals(4.48, m.contrast(0xFF777777.toInt(), 0xFFFFFFFF.toInt()), 0.02)
    }

    @Test
    fun lchRoundTripsInGamutColors() {
        listOf(0xFFE8A317, 0xFF1565C0, 0xFF00E676, 0xFF808080, 0xFF3A2A22).forEach { c ->
            val color = c.toInt()
            val lch = m.toLch(color)
            val back = m.fromLch(lch.l, lch.c, lch.h)
            for (shift in listOf(16, 8, 0)) {
                val a = (color shr shift) and 0xFF
                val b = (back shr shift) and 0xFF
                assertTrue("channel drift for ${Integer.toHexString(color)}", kotlin.math.abs(a - b) <= 1)
            }
        }
    }

    @Test
    fun accentRolesStayReadableInDarkAndLight() {
        for (dark in listOf(true, false)) {
            val backdrops = if (dark) darkBackdrops else lightBackdrops
            val text = if (dark) darkText else lightText
            for (seed in seeds) {
                val s = ArtScheme.from(seed, dark, backdrops, text)
                val tag = "seed=${Integer.toHexString(seed)} dark=$dark"
                assertTrue("seek/primary vs backdrop $tag", m.minContrast(s.primary, backdrops) >= ArtScheme.MIN_UI_CONTRAST)
                assertTrue("button text vs card $tag", m.minContrast(s.primary, text) >= ArtScheme.MIN_TEXT_CONTRAST)
                assertTrue("onPrimary $tag", m.contrast(s.primary, s.onPrimary) >= ArtScheme.MIN_TEXT_CONTRAST)
                assertTrue("primaryContainer $tag", m.contrast(s.primaryContainer, s.onPrimaryContainer) >= ArtScheme.MIN_TEXT_CONTRAST)
                assertTrue("secondaryContainer $tag", m.contrast(s.secondaryContainer, s.onSecondaryContainer) >= ArtScheme.MIN_TEXT_CONTRAST)
                assertTrue("onSecondary $tag", m.contrast(s.secondary, s.onSecondary) >= ArtScheme.MIN_TEXT_CONTRAST)
                assertEquals("seek active is the primary $tag", s.primary, s.seekActive)
            }
        }
    }

    @Test
    fun chromaticArtKeepsItsHueAndGreyArtStaysNeutral() {
        for (dark in listOf(true, false)) {
            val backdrops = if (dark) darkBackdrops else lightBackdrops
            for (seed in listOf(0xFFD32F2F.toInt(), 0xFF1565C0.toInt(), 0xFF00E676.toInt(), 0xFFE8A317.toInt())) {
                val s = ArtScheme.from(seed, dark, backdrops)
                val seedHue = m.toLch(seed).h
                val hue = m.toLch(s.primary).h
                val d = kotlin.math.abs(seedHue - hue).let { if (it > 180) 360 - it else it }
                assertTrue("hue drift $d for ${Integer.toHexString(seed)} dark=$dark", d <= 12.0)
                assertTrue("primary should be colorful", m.isChromatic(s.primary))
            }
            val grey = ArtScheme.from(0xFF808080.toInt(), dark, backdrops)
            assertTrue("grey art must not invent a hue", m.toLch(grey.primary).c < ArtColorMath.ACHROMATIC_CHROMA)
        }
    }

    @Test
    fun darkAndLightSchemesDifferForTheSameArt() {
        val seed = 0xFF1565C0.toInt()
        val dark = ArtScheme.from(seed, true, darkBackdrops)
        val light = ArtScheme.from(seed, false, lightBackdrops)
        assertTrue(m.tone(dark.primary) > m.tone(light.primary))
    }

    @Test
    fun pickSeedPrefersColorfulOverLargerGrey() {
        val grey = 0xFF7A7A7A.toInt() to 9000
        val red = 0xFFC62828.toInt() to 1200
        assertEquals(red.first, m.pickSeed(listOf(grey, red)))
    }

    @Test
    fun pickSeedFallsBackToDominantGreyForMonochromeArt() {
        val a = 0xFF202020.toInt() to 500
        val b = 0xFF9E9E9E.toInt() to 4000
        assertEquals(b.first, m.pickSeed(listOf(a, b)))
        assertNull(m.pickSeed(emptyList()))
    }
}
