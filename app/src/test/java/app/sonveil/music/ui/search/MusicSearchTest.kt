package app.sonveil.music.ui.search

import app.sonveil.music.data.remote.SearchResult3
import app.sonveil.music.data.remote.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MusicSearchTest {
    @Test fun pendingAndFailedRequestsAreNotEmptySuccess() = runTest {
        val search = MusicSearch(backgroundScope, { throw java.io.IOException("private URL") }, debounceMs = 10)
        search.setQuery("Miles")
        assertTrue(search.state.value.loading)
        assertFalse(search.state.value.hasSearched)
        advanceTimeBy(11); runCurrent()
        assertFalse(search.state.value.loading)
        assertNotNull(search.state.value.error)
        assertFalse(search.state.value.hasSearched)
        assertFalse(search.state.value.error!!.contains("private URL"))
    }
    @Test fun successEmptyAndRetryAreExplicit() = runTest {
        var fail = true
        val search = MusicSearch(backgroundScope, { if (fail) error("down") else SearchResult3() }, 0)
        search.setQuery("Miles"); runCurrent()
        fail = false; search.retry(); runCurrent()
        assertTrue(search.state.value.hasSearched)
        assertNull(search.state.value.error)
        assertTrue(search.state.value.result.song.isEmpty())
    }
    @Test fun cancelledQueryCannotPublishEvenIfTransportIgnoresCancellation() = runTest {
        val old = CompletableDeferred<SearchResult3>()
        val search = MusicSearch(backgroundScope, { req ->
            if (req.query == "old") withContext(NonCancellable) { old.await() }
            else SearchResult3(song = listOf(song("new")))
        }, 0)
        search.setQuery("old"); runCurrent()
        search.setQuery("new"); runCurrent()
        old.complete(SearchResult3(song = listOf(song("old")))); runCurrent()
        assertEquals("new", search.state.value.result.song.single().id)
    }
    @Test fun pageFailureRetainsResultsAndRetriesSameOffsetOnlyOnce() = runTest {
        var calls = 0
        var fail = true
        val search = MusicSearch(backgroundScope, { req ->
            calls++
            if (req.songOffset == 0) SearchResult3(song = (1..20).map { song("$it") })
            else if (fail) error("down") else SearchResult3(song = listOf(song("21")))
        }, 0)
        search.setQuery("Miles"); runCurrent()
        search.loadMore(); search.loadMore(); runCurrent()
        assertEquals(2, calls)
        assertEquals(20, search.state.value.result.song.size)
        assertNotNull(search.state.value.pageError)
        fail = false; search.loadMore(); runCurrent()
        assertEquals(21, search.state.value.result.song.size)
        assertNull(search.state.value.pageError)
    }
    @Test fun stalePageCannotMixIntoNewQuery() = runTest {
        val page = CompletableDeferred<SearchResult3>()
        val search = MusicSearch(backgroundScope, { req ->
            if (req.query == "new") SearchResult3(song = listOf(song("new")))
            else if (req.songOffset == 0) SearchResult3(song = (1..20).map { song("$it") })
            else withContext(NonCancellable) { page.await() }
        }, 0)
        search.setQuery("old"); runCurrent(); search.loadMore(); runCurrent()
        search.setQuery("new"); runCurrent()
        page.complete(SearchResult3(song = listOf(song("stale")))); runCurrent()
        assertEquals(listOf("new"), search.state.value.result.song.map { it.id })
    }
    @Test fun blankAndSingleCharacterDoNotPretendToSearch() = runTest {
        val search = MusicSearch(backgroundScope, { error("should not call server") }, 0)
        search.setQuery("a"); runCurrent()
        assertFalse(search.state.value.hasSearched)
        assertFalse(search.state.value.loading)
        search.setQuery(""); runCurrent()
        assertNull(search.state.value.error)
    }
    private fun song(id: String) = Song(id = id, title = id)
}
