package app.sonveil.music.data.player.auto

import app.sonveil.music.data.remote.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AutoQueueResolverTest {
    @Test fun idOnlyPlaylistRequestSelectsDuplicateOccurrenceAndKeepsMetadata() = runBlocking {
        val parent = AutoBrowseIds.playlist("mix/with ? unicode é")
        val songs = listOf(Song("same", duration = 123, coverArt = "cover"), Song("other"), Song("same"))
        val resolver = AutoQueueResolver { requested -> assertEquals(parent, requested); songs }
        val result = resolver.resolve(AutoBrowseIds.song("same", parent, 2))!!
        assertEquals(2, result.startIndex)
        assertEquals(songs, result.songs)
        assertEquals(123, result.songs[0].duration)
        assertEquals("cover", result.songs[0].coverArt)
    }

    @Test fun albumAndFavoritesResolveDirectlyWithoutRecentOrStarredLookup() = runBlocking {
        for (parent in listOf(AutoBrowseIds.album("new-not-recent"), AutoBrowseIds.FAVORITES)) {
            val songs = listOf(Song("one"), Song("not-starred"))
            val resolver = AutoQueueResolver { requested -> assertEquals(parent, requested); songs }
            assertEquals(1, resolver.resolve(AutoBrowseIds.song("not-starred", parent, 1))!!.startIndex)
            assertEquals(0, resolver.resolve(parent)!!.startIndex)
        }
    }

    @Test fun staleOccurrenceDoesNotPlayDifferentSong() = runBlocking {
        val parent = AutoBrowseIds.playlist("p")
        val resolver = AutoQueueResolver { listOf(Song("new")) }
        assertNull(resolver.resolve(AutoBrowseIds.song("old", parent, 0)))
        assertNull(resolver.resolve(AutoBrowseIds.song("new", parent, 4)))
    }

    @Test fun voiceQueueItemResolvesAgainstCurrentVoiceResults() = runBlocking {
        val songs = listOf(Song("first"), Song("second"))
        val resolver = AutoQueueResolver { parent ->
            assertEquals(AutoBrowseIds.VOICE, parent)
            songs
        }
        val id = AutoBrowseIds.song("second", AutoBrowseIds.VOICE, 1)
        assertEquals(1, resolver.resolve(id)!!.startIndex)
    }

    @Test fun malformedAndBareIdsCannotBecomeStreamRequests() = runBlocking {
        val resolver = AutoQueueResolver { error("Must not load unknown IDs") }
        for (id in listOf("bare-id", "https://example.com/song", "song/../0/a", "song/a/-1/b")) {
            assertNull(resolver.resolve(id))
        }
    }

    @Test fun folderKeysRoundTripWithoutSlashCollisions() {
        val id = "a/b?c%2Fé"
        assertEquals(id, AutoBrowseIds.parseAlbumId(AutoBrowseIds.album(id)))
        assertEquals(id, AutoBrowseIds.parsePlaylistId(AutoBrowseIds.playlist(id)))
        assertNotEquals(AutoBrowseIds.album("a/b"), AutoBrowseIds.album("a%2Fb"))
    }
}
