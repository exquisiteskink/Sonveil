package app.sonveil.music.data.player.auto

import org.junit.Assert.*
import org.junit.Test

class AutoClientGateTest {
    @Test fun gearheadIsCarHostEvenWhenUntrustedFlagFalse() {
        assertTrue(AutoClientGate.isCarMediaHost("com.google.android.projection.gearhead"))
        assertTrue(AutoClientGate.mayBrowseAndPlay(isTrusted = false, "com.google.android.projection.gearhead"))
    }

    @Test fun trustedUnknownPackageStillAllowed() {
        assertTrue(AutoClientGate.mayBrowseAndPlay(isTrusted = true, "com.example.random"))
    }

    @Test fun untrustedUnknownPackageRejected() {
        assertFalse(AutoClientGate.mayBrowseAndPlay(isTrusted = false, "com.malware.browse"))
        assertFalse(AutoClientGate.isCarMediaHost("com.malware.browse"))
        assertFalse(AutoClientGate.isCarMediaHost(null))
        assertFalse(AutoClientGate.isCarMediaHost(""))
    }

    @Test fun assistantAndAutomotiveHostsAllowed() {
        for (pkg in listOf(
            "com.android.car.media",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.googleassistant",
        )) {
            assertTrue(pkg, AutoClientGate.isCarMediaHost(pkg))
        }
    }
}
