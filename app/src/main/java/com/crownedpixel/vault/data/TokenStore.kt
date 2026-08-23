package com.crownedpixel.vault.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the GitHub token encrypted with a key that lives in the Android keystore and never
 * leaves it. The ciphertext sits in shared preferences; backup is disabled for the whole app,
 * so nothing here is ever synced off the device.
 */
class TokenStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("vault_credentials", Context.MODE_PRIVATE)

    fun readToken(): String? {
        val payload = prefs.getString(KEY_TOKEN, null) ?: return null
        return runCatching { decrypt(payload) }.getOrNull()
    }

    fun writeToken(token: String?) {
        val editor = prefs.edit()
        if (token.isNullOrBlank()) {
            editor.remove(KEY_TOKEN).remove(KEY_LOGIN).remove(KEY_SCOPES)
        } else {
            editor.putString(KEY_TOKEN, encrypt(token))
        }
        editor.apply()
    }

    var login: String?
        get() = prefs.getString(KEY_LOGIN, null)
        set(value) = prefs.edit().putString(KEY_LOGIN, value).apply()

    var scopes: String?
        get() = prefs.getString(KEY_SCOPES, null)
        set(value) = prefs.edit().putString(KEY_SCOPES, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val bytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":")
        require(parts.size == 2) { "malformed credential payload" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val bytes = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(bytes), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "vault_github_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_TOKEN = "token"
        const val KEY_LOGIN = "login"
        const val KEY_SCOPES = "scopes"
    }
}
