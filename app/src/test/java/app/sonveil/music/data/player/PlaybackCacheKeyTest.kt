package app.sonveil.music.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackCacheKeyTest {
    @Test fun reusesSongAcrossRequestsAndNormalizesServerSlash() {
        assertEquals(key(), PlaybackCacheKey.build(" https://music.test/ ", "user", "song", 320))
    }

    @Test fun separatesServerAccountSongAndQuality() {
        assertNotEquals(key(), PlaybackCacheKey.build("https://other.test", "user", "song", 320))
        assertNotEquals(key(), PlaybackCacheKey.build("https://music.test", "other", "song", 320))
        assertNotEquals(key(), PlaybackCacheKey.build("https://music.test", "user", "other", 320))
        assertNotEquals(key(), PlaybackCacheKey.build("https://music.test", "user", "song", 0))
    }

    @Test fun componentsCannotCollideOrExposeAccountIdentity() {
        assertNotEquals(PlaybackCacheKey.build("ab", "c", "song", 0), PlaybackCacheKey.build("a", "bc", "song", 0))
        assertFalse(key().contains("user"))
        assertFalse(key().contains("music.test"))
        assertFalse(key().contains("song"))
    }

    @Test fun negativeBitrateUsesOriginalQuality() {
        assertEquals(PlaybackCacheKey.build("server", "user", "song", 0), PlaybackCacheKey.build("server", "user", "song", -1))
    }

    private fun key() = PlaybackCacheKey.build("https://music.test", "user", "song", 320)
}
