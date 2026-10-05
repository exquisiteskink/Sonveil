package app.sonveil.music.data.art

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A bearer capability for one account, cover and size; never grants arbitrary cover access. */
internal object ArtworkAccess {
    fun token(namespace: String, account: String, coverId: String, size: Int): String {
        val message = listOf(account, coverId.trim(), size.coerceIn(32, 2048).toString())
            .joinToString("") { "${it.length}:$it" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(namespace.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun permits(namespace: String, account: String, coverId: String, size: Int, supplied: String?): Boolean {
        if (supplied == null || !supplied.matches(Regex("[a-f0-9]{64}"))) return false
        return MessageDigest.isEqual(
            token(namespace, account, coverId, size).toByteArray(Charsets.US_ASCII),
            supplied.toByteArray(Charsets.US_ASCII),
        )
    }
}
