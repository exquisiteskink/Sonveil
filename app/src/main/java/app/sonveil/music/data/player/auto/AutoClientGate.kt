package app.sonveil.music.data.player.auto

/**
 * Packages that must get a full Media3 library + player command set for Android Auto /
 * Automotive / Assistant hosts, even when [androidx.media3.session.MediaSession.ControllerInfo.isTrusted]
 * is false on a given device build.
 *
 * Hard-rejecting these hosts closes browse attach and leaves only background media-session
 * controls — the “in session but missing from AA app menu / not selectable” failure mode.
 */
object AutoClientGate {
    private val carMediaHosts = setOf(
        // Android Auto (phone projection)
        "com.google.android.projection.gearhead",
        // Android Automotive media / launcher hosts (OEM builds vary; common AOSP/GMS ids)
        "com.android.car.media",
        "com.android.car.carlauncher",
        "com.google.android.car.media",
        "com.google.android.carassistant",
        // Google Assistant / Gemini media browse (mobile + AAOS package names differ)
        "com.google.android.googlequicksearchbox",
        "com.google.android.as",
        "com.google.android.apps.googleassistant",
    )

    fun isCarMediaHost(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName in carMediaHosts
    }

    fun mayBrowseAndPlay(isTrusted: Boolean, packageName: String?): Boolean =
        isTrusted || isCarMediaHost(packageName)
}
