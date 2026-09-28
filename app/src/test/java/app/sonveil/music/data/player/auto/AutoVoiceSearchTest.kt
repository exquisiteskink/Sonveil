package app.sonveil.music.data.player.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoVoiceSearchTest {
    @Test fun exactMatchWinsOverEarlierPartialMatch() {
        val titles = listOf("Jazz at Night", "Jazz", "Jazz Morning")
        assertEquals("Jazz", bestMatch(titles, "jazz") { it })
    }

    @Test fun unrelatedSearchDoesNotStartPlayback() {
        assertNull(bestMatch(listOf("Blue Moon"), "jazz") { it })
        assertNull(bestMatch(listOf("Blue Moon"), " ") { it })
    }
}
