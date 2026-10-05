package app.sonveil.music.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stores server credentials in AES-256-GCM encrypted SharedPreferences.
 * The encryption key never leaves AndroidKeyStore. Auto-backup is disabled
 * for this file. Passwords are never written in plaintext and never logged.
 */
class CredentialStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): StoredCredentials? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val plain = decrypt(blob, iv)
            json.decodeFromString(StoredCredentials.serializer(), plain)
        } catch (_: Exception) {
            null
        }
    }

    fun save(credentials: StoredCredentials) {
        val (blob, iv) = encrypt(json.encodeToString(StoredCredentials.serializer(), credentials))
        prefs.edit()
            .putString(KEY_BLOB, blob)
            .putString(KEY_IV, iv)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasCredentials(): Boolean = prefs.contains(KEY_BLOB)

    private fun encrypt(plain: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val blob = Base64.encodeToString(
            cipher.doFinal(plain.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )
        return blob to iv
    }

    private fun decrypt(blob: String, ivB64: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        val bytes = cipher.doFinal(Base64.decode(blob, Base64.NO_WRAP))
        return String(bytes, Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "auralis_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS = "auralis_secure"
        private const val KEY_BLOB = "cred_blob"
        private const val KEY_IV = "cred_iv"
    }
}

@Serializable
data class StoredCredentials(
    val serverUrl: String,
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val authMode: AuthMode = AuthMode.Token,
    val transcodeBitrate: Int = 0,
    val allowInsecureLanHttp: Boolean = false,
)

@Serializable
enum class AuthMode {
    Token,
    HexPassword,
    ApiKey,
}
