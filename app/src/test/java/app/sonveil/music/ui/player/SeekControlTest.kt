package app.sonveil.music.ui.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.sonveil.music.data.player.SonveilPalette
import app.sonveil.music.ui.components.DetailLoadingScreen
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.SonveilTheme
import app.sonveil.music.ui.theme.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SeekControlTest {
    @get:Rule val compose = createComposeRule()

    @Test fun seekWorksWithAccessibilityActionAndKeyboard() {
        var sought = -1L
        compose.setContent {
            SonveilTheme(SonveilPalette.darkDefault(), ThemeMode.Dark) {
                FlatSeekBar(10_000, 60_000, { sought = it }, Modifier)
            }
        }
        val seek = compose.onNodeWithContentDescription("Playback position")
        seek.performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        compose.runOnIdle { assertEquals(30_000L, sought) }
        seek.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        seek.performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(15_000L, sought) }
    }
    @Test fun unknownDurationHasNoSeekAction() {
        compose.setContent { CompositionLocalProvider(LocalPalette provides SonveilPalette.darkDefault()) {
            FlatSeekBar(0, 0, { fail("Cannot seek unknown duration") })
        } }
        compose.onNodeWithContentDescription("Playback position").assertIsNotEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
    }
    @Test fun loadingAndErrorAlwaysKeepBackAndRetry() {
        var backs = 0
        var retries = 0
        compose.setContent { SonveilTheme(SonveilPalette.lightDefault(), ThemeMode.Light) {
            DetailLoadingScreen("Album", { backs++ }, "Could not load this music. Try again.", { retries++ })
        } }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, backs); assertEquals(1, retries) }
    }
}
