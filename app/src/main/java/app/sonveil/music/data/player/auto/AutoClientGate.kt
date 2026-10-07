package app.sonveil.music.data.player.auto

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Packages that must get a full Media3 library + player command set for Android Auto /
 * Automotive / Assistant hosts, even when [androidx.media3.session.MediaSession.ControllerInfo.isTrusted]
 * is false on a given device build.
 *
 * Package name alone is not enough: a sideloaded app can take an allowlisted name when the
 * real host is absent. Allowlisted hosts must also be a system image package or share a
 * signer with Play services / Play Store, or match the known Android Auto production cert.
 */
object AutoClientGate {
    private val carMediaHosts = setOf(
        "com.google.android.projection.gearhead",
        "com.android.car.media",
        "com.android.car.carlauncher",
        "com.google.android.car.media",
        "com.google.android.carassistant",
        "com.google.android.googlequicksearchbox",
        "com.google.android.as",
        "com.google.android.apps.googleassistant",
    )

    /** Android Auto production cert checked by platform permission policy. */
    internal val pinnedCertSha256 = setOf(
        "FDB00C43DBDE8B51CB312AA81D3B5FA17713ADB94B28F598D77F8EB89DACEEDF",
    )

    private val platformSigners = listOf(
        "com.google.android.gms",
        "com.android.vending",
    )

    data class HostIdentity(
        val packageName: String?,
        val trustedHost: Boolean,
    )

    fun isCarMediaHost(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName in carMediaHosts
    }

    fun mayBrowseAndPlay(isTrusted: Boolean, packageName: String?, trustedHost: Boolean): Boolean =
        isTrusted || (isCarMediaHost(packageName) && trustedHost)

    fun lookup(context: Context, packageName: String?): HostIdentity {
        if (packageName.isNullOrBlank()) return HostIdentity(packageName, false)
        val pm = context.packageManager
        val info = packageInfo(pm, packageName) ?: return HostIdentity(packageName, false)
        val flags = info.applicationInfo?.flags ?: 0
        val system = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val certs = certDigests(info)
        val platform = platformSigners.flatMap { certDigests(packageInfo(pm, it)) }.toSet()
        val trusted = system || certs.any { it in pinnedCertSha256 || it in platform }
        return HostIdentity(packageName, trusted)
    }

    private fun packageInfo(pm: PackageManager, packageName: String) = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
    }.getOrNull()

    private fun certDigests(info: android.content.pm.PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02X".format(it) }
        }.toSet()
    }
}
