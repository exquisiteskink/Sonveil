package app.sonveil.music.data.art

import org.junit.Assert.*
import org.junit.Test

class AlbumCoverIdsTest {
    @Test fun usesServerAlbumCoverAndReusesItWithinAccount() {
        val covers = AlbumCoverIds()
        assertEquals("album-cover", covers.resolve("account", "album") { "album-cover" })
        assertEquals("album-cover", covers.resolve("account", "album") { error("Should reuse album lookup") })
    }

    @Test fun separatesAlbumsAndAccounts() {
        val covers = AlbumCoverIds()
        covers.resolve("first", "same-id") { "first-cover" }
        assertEquals("second-cover", covers.resolve("second", "same-id") { "second-cover" })
        assertEquals("other-cover", covers.resolve("first", "other-id") { "other-cover" })
    }

    @Test fun missingAndFailedLookupsCanBeRetried() {
        val covers = AlbumCoverIds()
        assertNull(covers.resolve("account", "album") { null })
        assertThrows(java.io.IOException::class.java) {
            covers.resolve("account", "album") { throw java.io.IOException("offline") }
        }
        assertEquals("album-cover", covers.resolve("account", "album") { "album-cover" })
    }

    @Test fun boundsMetadataAndKeepsRecentlyUsedAlbums() {
        val covers = AlbumCoverIds(2)
        covers.resolve("account", "a") { "a-cover" }
        covers.resolve("account", "b") { "b-cover" }
        covers.resolve("account", "a") { error("Should still be cached") }
        covers.resolve("account", "c") { "c-cover" }
        assertEquals("refreshed-b", covers.resolve("account", "b") { "refreshed-b" })
    }

    @Test fun loadIsNotHeldUnderTheSharedMapLock() {
        // The load() lambda does a network getAlbum; it must not run while the covers map is
        // locked, or an unrelated resolve on another bucket stalls behind it.
        val covers = AlbumCoverIds()
        val loadEntered = java.util.concurrent.CountDownLatch(1)
        val releaseLoad = java.util.concurrent.CountDownLatch(1)
        val otherDone = java.util.concurrent.atomic.AtomicBoolean(false)
        val other = Thread { covers.resolve("account", "b") { "b-cover" }; otherDone.set(true) }
        other.start()
        // Hold this load() open; another bucket must resolve during it.
        Thread {
            loadEntered.countDown()
            covers.resolve("account", "a") { releaseLoad.await(); "a-cover" }
        }.start()
        loadEntered.await(2, java.util.concurrent.TimeUnit.SECONDS)
        other.join(2000)
        assertTrue("resolve on another bucket must not block on an in-flight load", otherDone.get())
        releaseLoad.countDown()
    }

    @Test fun concurrentResolveOfSameKeyLoadsOnce() {
        val covers = AlbumCoverIds()
        val start = java.util.concurrent.CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val workers = (1..8).map {
            Thread {
                start.await()
                covers.resolve("account", "album") {
                    calls.incrementAndGet()
                    Thread.sleep(30)
                    "album-cover"
                }
            }
        }
        workers.forEach { it.start() }
        start.countDown()
        workers.forEach { it.join() }
        assertEquals("load() must run once for concurrent resolves of one key", 1, calls.get())
    }
}
