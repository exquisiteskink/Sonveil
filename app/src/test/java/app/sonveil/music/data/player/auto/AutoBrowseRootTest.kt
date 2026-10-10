package app.sonveil.music.data.player.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBrowseRootTest {
    @Test fun rootIsFourTabsWithArtistsAndAlbums() {
        // Android Auto shows at most four root tabs; Artists and Albums must be among them.
        assertEquals(
            listOf(AutoBrowseIds.HOME, AutoBrowseIds.ARTISTS, AutoBrowseIds.ALBUMS, AutoBrowseIds.PLAYLISTS),
            AutoBrowseTree.rootOrder,
        )
    }

    @Test fun rootOrderCoversEveryBrowsableRootId() {
        // rootTabIds() derives from rootOrder, so every rendered root must resolve without
        // credentials; if a tab is added to rootOrder this stays green only if it is covered.
        val covered = setOf(
            AutoBrowseIds.HOME, AutoBrowseIds.ARTISTS, AutoBrowseIds.ALBUMS, AutoBrowseIds.PLAYLISTS,
            AutoBrowseIds.RECENT, AutoBrowseIds.FAVORITES, AutoBrowseIds.NEWEST,
        )
        assertTrue(AutoBrowseTree.rootOrder.all { it in covered })
    }

    @Test fun artistIdRoundTripsAndIsDistinctFromAlbum() {
        val id = AutoBrowseIds.artist("ar-1/x")
        assertEquals("ar-1/x", AutoBrowseIds.parseArtistId(id))
        assertNull(AutoBrowseIds.parseAlbumId(id))
        assertNull(AutoBrowseIds.parseArtistId(AutoBrowseIds.album("ar-1/x")))
    }
}
