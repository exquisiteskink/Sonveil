package app.sonveil.music.ui.player

import org.junit.Assert.*
import org.junit.Test

class PlayerLayoutPolicyTest {
    @Test fun artShrinksForShortWindowsAndLargeText() {
        val normal = playerLayout(400f, 800f, 1f)
        val large = playerLayout(400f, 800f, 2f)
        val short = playerLayout(400f, 400f, 1f)
        assertTrue(large.artSize < normal.artSize)
        assertTrue(short.artSize < normal.artSize)
        assertTrue(short.scrollMetadata)
    }
    @Test fun wideLayoutIsTwoPaneButNarrowLandscapeStillScrolls() {
        assertTrue(playerLayout(900f, 500f, 1f).twoPane)
        assertFalse(playerLayout(400f, 500f, 1f).twoPane)
        assertTrue(playerLayout(320f, 480f, 2f).scrollMetadata)
    }
    @Test fun seekClampsAndUnknownDurationCannotSeek() {
        assertEquals(0L, seekPosition(-1f, 1000))
        assertEquals(1000L, seekPosition(2f, 1000))
        assertEquals(0L, seekPosition(.5f, 0))
        assertEquals(0L, seekPosition(Float.NaN, 1000))
    }
}
