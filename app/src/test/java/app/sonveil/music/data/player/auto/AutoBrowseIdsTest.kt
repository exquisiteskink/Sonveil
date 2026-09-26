package app.sonveil.music.data.player.auto

import org.junit.Assert.*
import org.junit.Test

class AutoBrowseIdsTest {
    @Test fun legacyAuralisRootIdsNormalizeToSonveil() {
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
}
