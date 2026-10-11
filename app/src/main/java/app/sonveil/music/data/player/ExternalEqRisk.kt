package app.sonveil.music.data.player

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Detects when an **external** equalizer (especially Poweramp EQ + DVC) is likely
 * in play, so Sonveil can fail closed on dual-ExoPlayer crossfade.
 *
 * There is **no** public API for “DVC is currently on”. Package presence is the
 * durable signal: if Poweramp Equalizer is installed, dual-player CF is treated
 * as unsafe (second AudioTrack / promote teardown can leave PA unbound while
 * audible). Unknown EQs are a residual Option B-plus risk — see docs.
 */
object ExternalEqRisk {
    private const val TAG = "Sonveil/DvcSession"

    /** Poweramp Equalizer — primary DVC blast class. */
    const val POWERAMP_EQUALIZER = "com.maxmpz.equalizer"

    /**
     * Other session-insert EQs that historically interact badly with dual AudioTrack
     * on one session. Inclusion is fail-closed (disable dual CF), not a claim that
     * each has DVC.
     */
    val KNOWN_EXTERNAL_EQ_PACKAGES: Set<String> = setOf(
        POWERAMP_EQUALIZER,
        "com.pittvandewitt.wavelet",
        "com.mistersomov.wavelet",
    )

    /**
     * Pure policy: any installed package in [KNOWN_EXTERNAL_EQ_PACKAGES] ⇒ dual-player
     * crossfade is unsafe. Used by unit tests without PackageManager.
     */
    fun packagesImplyDualPlayerUnsafe(installedPackageNames: Collection<String>): Boolean =
        installedPackageNames.any { it in KNOWN_EXTERNAL_EQ_PACKAGES }

    /**
     * @return true when dual-ExoPlayer crossfade must not run (external EQ risk).
     * Fail closed: PackageManager errors ⇒ treat as unsafe.
     */
    fun isDualPlayerCrossfadeUnsafe(context: Context): Boolean {
        val pm = context.applicationContext.packageManager
        val present = mutableListOf<String>()
        for (pkg in KNOWN_EXTERNAL_EQ_PACKAGES) {
            if (isPackageInstalled(pm, pkg)) present += pkg
        }
        val unsafe = present.isNotEmpty()
        if (unsafe) {
            Log.i(TAG, "Option B: dual-player CF gated — external EQ packages=$present")
        }
        return unsafe
    }

    /** Convenience for settings copy / logging. */
    fun isPowerampEqualizerInstalled(context: Context): Boolean =
        isPackageInstalled(context.applicationContext.packageManager, POWERAMP_EQUALIZER)

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching {
            pm.getPackageInfo(packageName, 0)
            true
        }.getOrElse {
            // Fail closed only for the aggregate check when PM itself fails;
            // a missing package is a normal PackageManager.NameNotFoundException.
            if (it is PackageManager.NameNotFoundException) false
            else {
                Log.w(TAG, "PackageManager error for $packageName — fail-closed dual CF", it)
                true
            }
        }
}
