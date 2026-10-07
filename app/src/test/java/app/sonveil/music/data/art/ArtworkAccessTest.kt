package app.sonveil.music.data.art

import org.junit.Assert.*
import org.junit.Test

class ArtworkAccessTest {
    @Test fun capabilityIsBoundToAccountCoverSizeAndSession() {
        val token = ArtworkAccess.token("session-a", "account-a", "cover-a", 400)
        assertTrue(ArtworkAccess.permits("session-a", "account-a", "cover-a", 400, token))
        assertFalse(ArtworkAccess.permits("session-b", "account-a", "cover-a", 400, token))
        assertFalse(ArtworkAccess.permits("session-a", "account-b", "cover-a", 400, token))
        assertFalse(ArtworkAccess.permits("session-a", "account-a", "cover-b", 400, token))
        assertFalse(ArtworkAccess.permits("session-a", "account-a", "cover-a", 800, token))
        assertFalse(ArtworkAccess.permits("session-a", "account-a", "cover-a", 400, null))
        assertFalse(ArtworkAccess.permits("session-a", "account-a", "cover-a", 400, "0".repeat(64)))
        assertFalse(ArtworkAccess.permits("session-a", "account-a", "cover-a", 400, "malformed"))
    }

    @Test fun fieldsCannotBeRepartitionedAndIdsAreNormalized() {
        assertNotEquals(ArtworkAccess.token("session", "ab", "c", 400), ArtworkAccess.token("session", "a", "bc", 400))
        assertEquals(ArtworkAccess.token("session", "account", " cover ", 1), ArtworkAccess.token("session", "account", "cover", 32))
    }
}
