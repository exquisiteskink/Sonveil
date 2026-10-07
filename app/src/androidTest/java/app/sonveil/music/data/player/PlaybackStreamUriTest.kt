package app.sonveil.music.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackStreamUriTest {
    @Test fun locatorOmitsCredentials() {
        val uri = PlaybackStreamUri.build("song 1", 320, "nonce")
        val text = uri.toString()
        assertFalse(text.contains("apiKey"))
        assertFalse(text.contains("password"))
        assertEquals("song 1", PlaybackStreamUri.songId(uri))
        assertEquals(320, PlaybackStreamUri.bitrate(uri))
    }

    @Test fun installedAndroidAutoPassesHostVerification() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue(runCatching { context.packageManager.getPackageInfo("com.google.android.projection.gearhead", 0) }.isSuccess)
        val host = app.sonveil.music.data.player.auto.AutoClientGate.lookup(context, "com.google.android.projection.gearhead")
        assertEquals("com.google.android.projection.gearhead", host.packageName)
        org.junit.Assert.assertTrue("Installed Android Auto should be recognized by signer/system identity", host.trustedHost)
    }

    @Test fun rejectsOtherSchemes() {
        val uri = android.net.Uri.parse("https://example.test/rest/stream?id=1&apiKey=secret")
        assertNull(PlaybackStreamUri.songId(uri))
    }
}
