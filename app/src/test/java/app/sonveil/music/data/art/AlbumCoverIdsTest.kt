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
}
