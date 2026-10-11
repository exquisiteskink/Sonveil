package app.sonveil.music.data.player

import java.security.MessageDigest

/** Auth salts/nonces are request details, not the identity of the audio bytes. */
internal object PlaybackCacheKey {
    fun build(server: String, account: String, songId: String, bitrate: Int): String {
        val components = listOf(server.trim().trimEnd('/'), account, songId, bitrate.coerceAtLeast(0).toString())
        val value = components.joinToString("") { "${it.length}:$it" }
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return "audio-v1-" + digest.joinToString("") { "%02x".format(it) }
    }
}
