package app.sonveil.music.ui.player

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.sonveil.music.data.player.*
import app.sonveil.music.data.remote.Song
import app.sonveil.music.data.remote.SubsonicClient
import app.sonveil.music.ui.theme.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class NowPlayingLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val song = Song("one", title = "A very long title that should never push transport controls away",
        artist = "A long artist name", album = "A long album name", suffix = "flac", bitDepth = 24, samplingRate = 96000)

    @Test fun largeTextInShortPhoneKeepsQueueTransportAndMetadataReachable() {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
            SonveilTheme(SonveilPalette.lightDefault(), ThemeMode.Light) {
                Box(Modifier.size(320.dp, 480.dp)) {
                    NowPlayingContent(PlayerUiState(queue = listOf(song), durationMs = 60_000), 10_000, false,
                        {}, {}, {}, {}, {}, {}, {},
                        artwork = { Box(it) }, controls = { TextButton(onClick = {}) { Text("Play") } })
                }
            }
        } }
        compose.onNodeWithText("Play").assertIsDisplayed()
        compose.onNodeWithText("Up next · 0 songs").assertIsDisplayed()
        compose.onNodeWithContentDescription("Playback position").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Show lyrics").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Add to favorites").assertWidthIsAtLeast(48.dp)
    }

    @Test fun miniPlayerReportsItsActualLargeTextHeight() {
        val client = SubsonicClient()
        var height = 0
        compose.setContent { CompositionLocalProvider(LocalClient provides client,
            LocalDensity provides Density(1f, 2f)) {
            SonveilTheme(SonveilPalette.darkDefault(), ThemeMode.Dark) {
                Box(Modifier.width(320.dp)) {
                    MiniBar(PlayerUiState(queue = listOf(song)), 0, onPlayPause = {}, onHeightChanged = { height = it })
                }
            }
        } }
        compose.runOnIdle { org.junit.Assert.assertTrue("Actual mini-player height: $height", height > SonveilSize.MiniPlayer.value) }
    }

    @Test fun sustainedBufferingIsAnnouncedButBriefStallsAreNot() {
        compose.mainClock.autoAdvance = false
        val state = mutableStateOf(PlayerUiState(playWhenReady = true, playbackState = androidx.media3.common.Player.STATE_BUFFERING))
        compose.setContent { SonveilTheme(SonveilPalette.darkDefault(), ThemeMode.Dark) { PlaybackStatus(state.value) } }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Buffering…").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Buffering…").assertExists()
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { state.value = state.value.copy(playWhenReady = false) }
        compose.waitForIdle()
        compose.onNodeWithText("Buffering…").assertDoesNotExist()
    }

    @Test fun hiddenPlayerAndQueueAreNotExposedUntilOpened() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = SubsonicClient()
        val player = PlayerController(context, client)
        val state = mutableStateOf(PlayerUiState(queue = listOf(Song("one", title = "Test song"), Song("two", title = "Next song"))))
        compose.setContent {
            CompositionLocalProvider(LocalClient provides client, LocalPlayer provides player, LocalPlayerState provides state) {
                SonveilTheme(SonveilPalette.darkDefault(), ThemeMode.Dark) {
                    NowPlayingHost(remember { mutableFloatStateOf(0f) }, {}, false)
                }
            }
        }
        compose.onNodeWithContentDescription("Close player").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to player").assertDoesNotExist()
        compose.onNodeWithText("Test song").performClick()
        compose.onNodeWithContentDescription("Close player").assertExists()
        compose.onNodeWithText("Up next · 1 songs").performClick()
        compose.onNodeWithContentDescription("Back to player").assertExists()
        compose.onNodeWithContentDescription("Close player").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to player").performClick()
        compose.onNodeWithContentDescription("Close player").assertExists()
    }
}
