package app.sonveil.music.data.player

import app.sonveil.music.data.remote.Song
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PlayerUiStateTest {
    @Test fun bufferingPreservesPauseAgencyAndIsNotAnError() {
        val state = PlayerUiState(playbackState = androidx.media3.common.Player.STATE_BUFFERING, playWhenReady = true)
        assertTrue(state.isBuffering)
        assertTrue(state.showPause)
        assertNull(state.playbackError)
        assertFalse(state.copy(playWhenReady = false).showPause)
        assertFalse(state.copy(playWhenReady = false).isBuffering)
        assertFalse(state.copy(playbackError = "Connection error").showPause)
    }

    @Test fun shuffleMovesCurrentToTopAndKeepsEveryQueueEntry() {
        val songs = listOf(Song("a"), Song("b"), Song("a"), Song("c"))
        val shuffled = shuffledQueueFromCurrent(songs, 2, Random(17))

        assertSame(songs[2], shuffled.first())
        assertEquals(songs.size, shuffled.size)
        assertEquals(songs.indices.toList(), shuffled.map { song -> songs.indexOfFirst { it === song } }.sorted())
        assertEquals(shuffled.drop(1), PlayerUiState(queue = shuffled, shuffle = true).upNext)
    }

    @Test fun shuffledQueueKeepsActualIndicesIncludingDuplicateSongs() {
        val songs = listOf(Song("a"), Song("b"), Song("a"), Song("c"))
        val state = PlayerUiState(queue = songs, index = 1, shuffle = true, upcomingIndices = listOf(3, 0, 2))
        assertEquals(listOf("c", "a", "a"), state.upNext.map { it.id })
        assertEquals(listOf(3, 0, 2), state.upNextIndices)
        assertTrue(state.copy(upcomingIndices = emptyList()).upNext.isEmpty())
    }

    @Test fun favoriteOverrideTakesPrecedenceOverStaleSongMetadata() {
        val favorite = Song("a", starred = "2026-09-18")
        assertFalse(PlayerUiState(favoriteById = mapOf("a" to false)).isFavorite(favorite))
        assertTrue(PlayerUiState(favoriteById = mapOf("a" to true)).isFavorite(favorite.copy(starred = null)))
        assertTrue(PlayerUiState().isFavorite(favorite))
    }
}
