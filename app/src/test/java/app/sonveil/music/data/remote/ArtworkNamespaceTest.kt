package app.sonveil.music.data.remote

import app.sonveil.music.data.art.ArtworkAccess
import org.junit.Assert.assertTrue
import app.sonveil.music.data.auth.StoredCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArtworkNamespaceTest {
    private val acct = StoredCredentials(serverUrl = "https://music.example/", username = "me", apiKey = "k")

    @Test fun revalidatingSameAccountKeepsIssuedArtworkUrisValid() {
        val client = SubsonicClient()
        client.credentials = acct // restoreSession publishes stored creds before login() re-validates
        val before = client.artworkNamespace
        val token = ArtworkAccess.token(before, "scope", "al-1", 800)
        client.onAuthenticated(acct, acct.copy(serverUrl = "https://music.example"))
        assertEquals(before, client.artworkNamespace)
        assertTrue(ArtworkAccess.permits(client.artworkNamespace, "scope", "al-1", 800, token))
    }

    @Test fun switchingAccountRotatesNamespace() {
        val client = SubsonicClient()
        val before = client.artworkNamespace
        client.onAuthenticated(acct, acct.copy(username = "other"))
        assertNotEquals(before, client.artworkNamespace)
    }

    @Test fun logoutStillRotates() {
        val client = SubsonicClient()
        val before = client.artworkNamespace
        client.rotateSessionSalt()
        assertNotEquals(before, client.artworkNamespace)
    }
}
