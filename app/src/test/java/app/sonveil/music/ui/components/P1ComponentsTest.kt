package app.sonveil.music.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.sonveil.music.data.player.PlayerController
import app.sonveil.music.data.player.PlayerUiState
import app.sonveil.music.data.player.SonveilPalette
import app.sonveil.music.data.remote.Playlist
import app.sonveil.music.data.remote.Song
import app.sonveil.music.data.remote.SubsonicClient
import app.sonveil.music.ui.home.PlaylistPreview
import app.sonveil.music.ui.playlist.PlaylistsContent
import app.sonveil.music.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class P1ComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun navigationShowsAllLabelsAndPreservesSelectedRoute() {
        var route = ""
        compose.setContent { SonveilTheme(SonveilPalette.darkDefault(), ThemeMode.Dark) {
            SonveilNavigationBar("artists", { route = it })
        } }
        listOf("Home", "Artists", "Search", "Settings").forEach {
            compose.onNodeWithText(it).assertIsDisplayed().assertHasClickAction()
        }
        compose.onNodeWithText("Artists").assertIsSelected()
        compose.onNodeWithText("Search").performClick()
        compose.runOnIdle { assertEquals("search", route) }
    }

    @Test fun previewIsBoundedAndSeeAllIsAnExplicitAction() {
        var opened = ""
        var seeAll = false
        val playlists = (1..20).map { Playlist("$it", "Playlist $it") }
        compose.setContent { SonveilTheme(SonveilPalette.lightDefault(), ThemeMode.Light) {
            PlaylistPreview(playlists, { opened = it }, { seeAll = true })
        } }
        compose.onNodeWithText("Playlist 6").assertExists()
        compose.onNodeWithText("Playlist 7").assertDoesNotExist()
        compose.onNodeWithText("See all").performClick()
        compose.onNodeWithText("Playlist 1").performClick()
        compose.runOnIdle { assertTrue(seeAll); assertEquals("1", opened) }
    }

    @Test fun emptyPreviewDoesNotOfferADeadSeeAllAction() {
        compose.setContent { SonveilTheme(SonveilPalette.lightDefault(), ThemeMode.Light) {
            PlaylistPreview(emptyList(), {}, {})
        } }
        compose.onNodeWithText("See all").assertDoesNotExist()
    }

    @Test fun failedRefreshRetainsConfirmedPlaylists() {
        compose.setContent { SonveilTheme(SonveilPalette.darkDefault(), ThemeMode.Dark) {
            PlaylistsContent(listOf(Playlist("one", "Confirmed playlist")), false, "Server unavailable", {}, {}, {})
        } }
        compose.onNodeWithText("Confirmed playlist").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Server unavailable").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertHasClickAction()
    }

    @Test fun allPlaylistsListCanReachBeyondPreviewAndKeepBack() {
        var opened = ""
        var back = false
        compose.setContent { SonveilTheme(SonveilPalette.darkDefault(), ThemeMode.Dark) {
            PlaylistsContent((1..100).map { Playlist("$it", "Playlist $it") },
                loading = false, error = null, onRetry = {}, onBack = { back = true }, onPlaylist = { opened = it })
        } }
        compose.onNodeWithTag("Playlists list").performScrollToNode(hasText("Playlist 100"))
        compose.onNodeWithText("Playlist 100").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals("100", opened); assertTrue(back) }
    }

    @Test fun failedPlaylistReadDoesNotPretendToBeEmptyAndKeepsRetryAndBack() {
        compose.setContent { SonveilTheme(SonveilPalette.lightDefault(), ThemeMode.Light) {
            PlaylistsContent(emptyList(), false, "Server unavailable", {}, {}, {})
        } }
        compose.onNodeWithText("Server unavailable").assertIsDisplayed()
        compose.onNodeWithText("No playlists yet").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertHasClickAction()
        compose.onNodeWithText("Try again").assertHasClickAction()
    }

    @Test fun songDurationIsTrailingRatherThanAThirdIdentityLine() {
        val client = SubsonicClient()
        val player = PlayerController(ApplicationProvider.getApplicationContext<Context>(), client)
        compose.setContent { CompositionLocalProvider(LocalClient provides client, LocalPlayer provides player,
            LocalPlayerState provides mutableStateOf(PlayerUiState())) {
            SonveilTheme(SonveilPalette.lightDefault(), ThemeMode.Light) {
                Box(Modifier.width(360.dp)) {
                    SongRow(Song("one", title = "Track", artist = "Artist", duration = 183), {}, showCover = false)
                }
            }
        } }
        val title = compose.onNodeWithText("Track", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val artist = compose.onNodeWithText("Artist", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val duration = compose.onNodeWithText("3:03", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("Duration should sit alongside identity", duration.top < artist.top)
        assertTrue("Duration should trail the identity column", duration.left > title.right)
        compose.onNodeWithContentDescription("More actions for Track").assertHasClickAction()
    }
}
