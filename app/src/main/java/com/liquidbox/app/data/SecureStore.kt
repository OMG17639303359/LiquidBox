package com.liquidbox.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.liquidbox.app.LiquidBoxApp
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore 加密存储。
 * API Key 使用 AES-256-GCM 加密后存入 SharedPreferences，密钥永不导出。
 */
object SecureStore {
    private const val PREF = "ai_secure"
    private const val KEY_ALIAS = "liquidbox_ai_key_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private val prefs: SharedPreferences
        get() = LiquidBoxApp.appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val bytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

    fun decrypt(payload: String?): String? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            val (ivStr, dataStr) = payload.split(":", limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(ivStr, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(dataStr, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun putEncrypted(key: String, value: String) {
        encrypt(value)?.let { prefs.edit().putString(key, it).apply() }
    }

    fun getDecrypted(key: String): String? = decrypt(prefs.getString(key, null))

    fun putPlain(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getPlain(key: String, default: String = ""): String = prefs.getString(key, default) ?: default
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)
    fun remove(key: String) = prefs.edit().remove(key).apply()
}
