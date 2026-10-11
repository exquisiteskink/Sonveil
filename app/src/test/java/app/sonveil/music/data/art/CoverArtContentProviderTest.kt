package app.sonveil.music.data.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.sonveil.music.data.auth.AuthMode
import app.sonveil.music.data.auth.StoredCredentials

/** Pure path encode/parse tests (no Android runtime / Robolectric). */
class CoverArtContentProviderTest {

    @Test
    fun accountScope_isStableWithinSessionAndSeparatesServersAndAccounts() {
        val credentials = StoredCredentials("https://music.example/", "alice", "password")
        fun scopeFor(creds: StoredCredentials) = CoverArtContentProvider.accountScope(creds, "private-random-namespace")
        val scope = scopeFor(credentials)
        assertEquals(scope, scopeFor(credentials.copy(serverUrl = "https://music.example")))
        assertEquals(scope, scopeFor(credentials.copy(transcodeBitrate = 192)))
        assertEquals(scope, scopeFor(credentials.copy(password = "other-password")))
        assertTrue(scope != CoverArtContentProvider.accountScope(credentials, "different-private-namespace"))
        for (other in listOf(credentials.copy(username = "bob"),
            credentials.copy(serverUrl = "https://other.example"),
            credentials.copy(authMode = AuthMode.ApiKey, apiKey = "key-a"),
            credentials.copy(authMode = AuthMode.ApiKey, apiKey = "key-b"))) {
            assertTrue(scope != scopeFor(other))
        }
        assertTrue(scope.matches(Regex("[a-f0-9]{64}")))
    }

    @Test
    fun buildEncodedPath_blank_returnsNull() {
        assertNull(CoverArtContentProvider.buildEncodedPath(null, 400))
        assertNull(CoverArtContentProvider.buildEncodedPath("", 400))
        assertNull(CoverArtContentProvider.buildEncodedPath("  ", 400))
    }

    @Test
    fun buildEncodedPath_roundTrip() {
        val path = CoverArtContentProvider.buildEncodedPath("al-42", 400)!!
        assertEquals("/cover/400/al-42", path)
        val parsed = CoverArtContentProvider.parseEncodedPath(path)!!
        assertEquals("al-42", parsed.coverId)
        assertEquals(400, parsed.size)
    }

    @Test
    fun buildEncodedPath_clampsSize() {
        assertEquals(32, CoverArtContentProvider.parseEncodedPath(
            CoverArtContentProvider.buildEncodedPath("x", 1)!!,
        )!!.size)
        assertEquals(2048, CoverArtContentProvider.parseEncodedPath(
            CoverArtContentProvider.buildEncodedPath("x", 99999)!!,
        )!!.size)
    }

    @Test
    fun buildEncodedPath_encodesSlashInCoverId() {
        val path = CoverArtContentProvider.buildEncodedPath("a/b", 800)!!
        assertTrue(path.startsWith("/cover/800/"))
        assertTrue(path.contains("%2F") || path.contains("%2f"))
        val parsed = CoverArtContentProvider.parseEncodedPath(path)!!
        assertEquals("a/b", parsed.coverId)
        assertEquals(800, parsed.size)
    }

    @Test
    fun parseEncodedPath_rejectsTraversal() {
        assertNull(CoverArtContentProvider.parseEncodedPath("/cover/400/..%2Fetc"))
        assertNull(CoverArtContentProvider.parseEncodedPath("/cover/400/foo/../bar"))
    }

    @Test
    fun parseEncodedPath_rejectsWrongShape() {
        assertNull(CoverArtContentProvider.parseEncodedPath("/other/400/al-1"))
        assertNull(CoverArtContentProvider.parseEncodedPath("/cover/x/al-1"))
        assertNull(CoverArtContentProvider.parseEncodedPath(null))
    }

    @Test
    fun albumPathKeepsAlbumIdentitySeparateFromCoverIdentity() {
        val path = CoverArtContentProvider.buildEncodedPath("album/id", 800, isAlbum = true)!!
        val parsed = CoverArtContentProvider.parseEncodedPath(path)!!
        assertEquals("album/id", parsed.coverId)
        assertTrue(parsed.isAlbum)
        assertEquals(false, CoverArtContentProvider.parseEncodedPath("/cover/800/album%2Fid")!!.isAlbum)
        assertNull(CoverArtContentProvider.parseEncodedPath("/album/800/..%2Fetc"))
    }

    @Test
    fun authority_matchesApplicationIdSuffix() {
        assertEquals("app.sonveil.music.coverart", CoverArtContentProvider.AUTHORITY)
    }
}
