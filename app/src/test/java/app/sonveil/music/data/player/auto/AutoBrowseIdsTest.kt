package app.sonveil.music.data.player.auto

import org.junit.Assert.*
import org.junit.Test

class AutoBrowseIdsTest {
    @Test fun legacySonveilRootIdsNormalizeToSonveil() {
        assertEquals(AutoBrowseIds.ROOT, AutoBrowseIds.normalizeParentId("auralis_root"))
        assertEquals(AutoBrowseIds.PLAYLISTS, AutoBrowseIds.normalizeParentId("auralis_playlists"))
        assertEquals(AutoBrowseIds.FAVORITES, AutoBrowseIds.normalizeParentId("auralis_favorites"))
        assertTrue(AutoBrowseIds.isRoot("auralis_root"))
        assertTrue(AutoBrowseIds.isFavorites("auralis_favorites"))
    }

    @Test fun songParentLegacyFavoritesStillParses() {
        val mediaId = AutoBrowseIds.song("s1", "auralis_favorites", 0)
        val ref = AutoBrowseIds.parseSong(mediaId)!!
        assertEquals(AutoBrowseIds.FAVORITES, ref.parent)
        assertEquals("s1", ref.songId)
    }

    @Test fun artistIdsRoundTripOpaqueServerIds() {
        listOf("ar-123", "artist/with spaces", "Björk:日本語").forEach { id ->
            assertEquals(id, AutoBrowseIds.parseArtistId(AutoBrowseIds.artist(id)))
        }
    }

    @Test fun malformedArtistIdsCannotResolveOtherFoldersOrSongQueues() {
        assertNull(AutoBrowseIds.parseArtistId(AutoBrowseIds.album("ar-123")))
        assertNull(AutoBrowseIds.parseArtistId("artist/"))
        assertNull(AutoBrowseIds.parseArtistId("artist/%%%"))
        assertNull(AutoBrowseIds.parseArtistId("artist/YXItMTIz/extra"))
        assertNull(AutoBrowseIds.parseSong(AutoBrowseIds.song("s1", AutoBrowseIds.artist("a1"), 0)))
    }

    @Test fun selectedArtistAlbumSongsKeepTheirAlbumParentAndTrackOccurrence() {
        val parent = AutoBrowseIds.album("album-from-artist")
        val ref = AutoBrowseIds.parseSong(AutoBrowseIds.song("s1", parent, 4))!!
        assertEquals(parent, ref.parent)
        assertEquals("album-from-artist", AutoBrowseIds.parseAlbumId(ref.parent))
        assertEquals(4, ref.index)
    }
}
