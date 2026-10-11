package app.sonveil.music.data.player.auto

import androidx.media3.common.MediaMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sonveil.music.SonveilApp
import app.sonveil.music.data.auth.StoredCredentials
import app.sonveil.music.data.remote.ArtistID3
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoArtistBrowseTest {
    @Test fun artistsAndRecentlyAddedAreReachableWithFourBrowsableTabs() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SonveilApp
        val client = app.container.client
        val previous = client.credentials
        try {
            client.credentials = StoredCredentials("https://test.invalid", "test")
            val factory = AutoMediaItemFactory(client, app.packageName) { 0 }
            val tree = AutoBrowseTree(app.container, factory)
            val tabs = tree.childrenOf(AutoBrowseIds.ROOT)
            assertEquals(4, tabs.size)
            assertTrue(tabs.all { it.mediaMetadata.isBrowsable == true && it.mediaMetadata.isPlayable == false })
            assertTrue(tabs.any { it.mediaId == AutoBrowseIds.LIBRARY })
            val library = tree.childrenOf(AutoBrowseIds.LIBRARY)
            assertEquals(listOf(AutoBrowseIds.ARTISTS, AutoBrowseIds.NEWEST), library.map { it.mediaId })
            assertEquals("All artists", tree.item(AutoBrowseIds.ARTISTS)?.mediaMetadata?.title)
            assertEquals(AutoBrowseIds.NEWEST, tree.item("auralis_newest")?.mediaId)
        } finally {
            client.credentials = previous
        }
    }

    @Test fun artistIsBrowsableAndUsesServerArtistIdentity() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SonveilApp
        val factory = AutoMediaItemFactory(app.container.client, app.packageName) { 0 }
        val item = factory.artist(ArtistID3("opaque/artist-id", "Björk", albumCount = 12))
        assertEquals("opaque/artist-id", AutoBrowseIds.parseArtistId(item.mediaId))
        assertEquals("Björk", item.mediaMetadata.title)
        assertEquals("12 albums", item.mediaMetadata.subtitle)
        assertEquals(MediaMetadata.MEDIA_TYPE_ARTIST, item.mediaMetadata.mediaType)
        assertEquals(true, item.mediaMetadata.isBrowsable)
        assertEquals(false, item.mediaMetadata.isPlayable)
    }
}
