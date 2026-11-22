package dev.vis.fsb.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the FSB API access token encrypted with an AES-GCM key that lives in the Android Keystore
 * (the key never leaves secure hardware where available). If the key is missing — e.g. after a
 * backup restore onto another device — the stored token can't be decrypted and is discarded.
 */
class TokenStore(context: Context) {
    private val sp = context.getSharedPreferences("fsb_auth", Context.MODE_PRIVATE)

    @Volatile
    private var cached: String? = null
    private var loaded = false

    @get:Synchronized
    val token: String?
        get() {
            if (!loaded) {
                cached = runCatching { read() }.getOrNull()
                loaded = true
                if (cached == null && sp.contains(KEY_DATA)) sp.edit { remove(KEY_DATA) }
            }
            return cached
        }

    @Synchronized
    fun save(token: String) {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val blob = c.iv + c.doFinal(token.toByteArray(Charsets.UTF_8))
        sp.edit { putString(KEY_DATA, Base64.encodeToString(blob, Base64.NO_WRAP)) }
        cached = token
        loaded = true
    }

    @Synchronized
    fun clear() {
        sp.edit { remove(KEY_DATA) }
        cached = null
        loaded = true
    }

    private fun read(): String? {
        val b64 = sp.getString(KEY_DATA, null) ?: return null
        val blob = Base64.decode(b64, Base64.NO_WRAP)
        if (blob.size <= IV_LEN) return null
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(128, blob, 0, IV_LEN))
        return String(c.doFinal(blob, IV_LEN, blob.size - IV_LEN), Charsets.UTF_8)
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingKey(): SecretKey? = (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    private companion object {
        const val ALIAS = "fsb_api_token_key"
        const val KEY_DATA = "token"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
