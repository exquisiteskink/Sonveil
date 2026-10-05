package app.sonveil.music.data.player

import android.net.Uri

/**
 * Opaque playback locator. Controllers and session state can see this URI.
 * The player resolves it to an authenticated Subsonic stream URL in-process.
 */
object PlaybackStreamUri {
    const val SCHEME = "sonveil"
    const val HOST = "stream"

    fun build(songId: String, bitrate: Int, nonce: String): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter("id", songId)
            .appendQueryParameter("br", bitrate.coerceAtLeast(0).toString())
            .appendQueryParameter("n", nonce)
            .build()

    fun songId(uri: Uri): String? {
        if (!uri.scheme.equals(SCHEME, ignoreCase = true) || !uri.host.equals(HOST, ignoreCase = true)) return null
        return uri.getQueryParameter("id")?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun bitrate(uri: Uri): Int = uri.getQueryParameter("br")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
}
