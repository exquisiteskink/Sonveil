package app.sonveil.music.ui.album

import app.sonveil.music.data.remote.AlbumID3
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AlbumPagerTest {
    @Test fun laterFailureKeepsAlbumsAndRetriesTheUnadvancedOffset() = runTest {
        var fail = true
        val offsets = mutableListOf<Int>()
        val pager = AlbumPager(backgroundScope, { _, offset ->
            offsets += offset
            if (offset == 0) listOf(album("1"), album("2"))
            else if (fail) error("down") else listOf(album("3"))
        }, pageSize = 2)
        pager.selectFolder(null); runCurrent()
        pager.loadMore(); pager.loadMore(); runCurrent()
        assertEquals(2, pager.state.value.albums.size)
        assertTrue(pager.state.value.more)
        assertNotNull(pager.state.value.error)
        fail = false; pager.loadMore(); runCurrent()
        assertEquals(listOf(0, 2, 2), offsets)
        assertEquals(3, pager.state.value.albums.size)
        assertFalse(pager.state.value.more)
    }
    @Test fun oldFolderCannotPublishIntoNewFolder() = runTest {
        val old = CompletableDeferred<List<AlbumID3>>()
        val pager = AlbumPager(backgroundScope, { folder, _ ->
            if (folder == "old") withContext(NonCancellable) { old.await() } else listOf(album("new"))
        })
        pager.selectFolder("old"); runCurrent()
        pager.selectFolder("new"); runCurrent()
        old.complete(listOf(album("old"))); runCurrent()
        assertEquals("new", pager.state.value.albums.single().id)
    }
    private fun album(id: String) = AlbumID3(id = id, name = id)
}
