package app.sonveil.music.data.player.auto

import org.junit.Assert.*
import org.junit.Test

class AutoClientGateTest {
    @Test fun gearheadNameAloneIsNotEnough() {
        assertTrue(AutoClientGate.isCarMediaHost("com.google.android.projection.gearhead"))
        assertFalse(AutoClientGate.mayBrowseAndPlay(isTrusted = false, "com.google.android.projection.gearhead", trustedHost = false))
    }

    @Test fun signedOrSystemAllowlistedHostMayBrowse() {
        assertTrue(AutoClientGate.mayBrowseAndPlay(isTrusted = false, "com.google.android.projection.gearhead", trustedHost = true))
        assertTrue(AutoClientGate.mayBrowseAndPlay(isTrusted = false, "com.android.car.media", trustedHost = true))
    }

    @Test fun trustedUnknownPackageStillAllowed() {
        assertTrue(AutoClientGate.mayBrowseAndPlay(isTrusted = true, "com.example.random", trustedHost = false))
    }

    @Test fun untrustedUnknownPackageRejected() {
        assertFalse(AutoClientGate.mayBrowseAndPlay(isTrusted = false, "com.malware.browse", trustedHost = true))
        assertFalse(AutoClientGate.isCarMediaHost("com.malware.browse"))
        assertFalse(AutoClientGate.isCarMediaHost(null))
        assertFalse(AutoClientGate.isCarMediaHost(""))
    }

    @Test fun assistantAndAutomotiveHostsRecognized() {
        for (pkg in listOf(
            "com.android.car.media",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.googleassistant",
        )) {
            assertTrue(pkg, AutoClientGate.isCarMediaHost(pkg))
        }
    }
}
