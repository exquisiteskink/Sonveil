package app.sonveil.music.data.player.auto

import org.junit.Assert.*
import org.junit.Test

class AutoBrowsePagingTest {
    @Test fun nonPositivePageSizeReturnsAllForAndroidAuto() {
        val all = listOf("a", "b", "c", "d")
        assertEquals(all, AutoBrowsePaging.slice(all, page = 0, pageSize = 0))
        assertEquals(all, AutoBrowsePaging.slice(all, page = 0, pageSize = -1))
    }

    @Test fun positivePageSizeSlices() {
        val all = (0 until 10).toList()
        assertEquals(listOf(0, 1, 2), AutoBrowsePaging.slice(all, 0, 3))
        assertEquals(listOf(3, 4, 5), AutoBrowsePaging.slice(all, 1, 3))
        assertEquals(emptyList<Int>(), AutoBrowsePaging.slice(all, 5, 3))
    }

    @Test fun negativePageRejected() {
        assertNull(AutoBrowsePaging.slice(listOf(1), page = -1, pageSize = 10))
    }

    @Test fun carHostReceivesEveryArtistEvenWithSmallPositivePageSize() {
        val allArtists = (0 until 1500).toList()
        assertEquals(allArtists, AutoBrowsePaging.forClient(allArtists, 0, 50, isCarHost = true))
    }

    @Test fun phoneClientRetainsPaginationAndInvalidPageIsRejected() {
        val all = (0 until 100).toList()
        assertEquals((20 until 40).toList(), AutoBrowsePaging.forClient(all, 1, 20, isCarHost = false))
        assertNull(AutoBrowsePaging.forClient(all, -1, 20, isCarHost = true))
    }
}
