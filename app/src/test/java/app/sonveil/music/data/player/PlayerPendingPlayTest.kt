package app.sonveil.music.data.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sonveil.music.data.remote.Song
import app.sonveil.music.data.remote.SubsonicClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PlayerPendingPlayTest {
    @Test fun pauseBeforeSessionConnectionCancelsPendingAutoplayWithoutLosingQueue() {
        val client = SubsonicClient().apply {
            credentials = app.sonveil.music.data.auth.StoredCredentials("https://example.invalid", "test-listener")
        }
        val player = PlayerController(ApplicationProvider.getApplicationContext<Context>(), client)
        // Represent an unfinished connection without binding a real playback service.
        val connection = com.google.common.util.concurrent.SettableFuture.create<androidx.media3.session.MediaController>()
        PlayerController::class.java.getDeclaredField("connection").apply { isAccessible = true }.set(player, connection)
        val songs = listOf(Song("one", title = "First"), Song("two", title = "Second"))
        try {
            player.play(songs, 1)
            assertTrue(player.state.value.isBuffering)
            assertTrue(player.state.value.showPause)
            player.playPause()
            assertFalse(player.state.value.playWhenReady)
            assertFalse(player.state.value.isBuffering)
            assertEquals(songs, player.state.value.queue)
            assertEquals(1, player.state.value.index)
            player.playPause()
            assertTrue(player.state.value.playWhenReady)
            assertEquals(1, player.state.value.index)
        } finally { player.stopAndReset(); connection.cancel(true) }
    }
}
