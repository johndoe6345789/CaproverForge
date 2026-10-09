package com.caproverforge.data

import android.content.Context
import androidx.core.content.edit
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [monitorCookie] is the `captainCookieAuth` cookie CapRover sets at login. Its NetData proxy
 * (`/net-data-monitor/`) only accepts that cookie, not the API token.
 */
data class Session(val baseUrl: String, val token: String, val monitorCookie: String? = null)

/**
 * Persists the server address and the CapRover auth token. The token is encrypted with an
 * AES-GCM key that never leaves the Android Keystore. The password itself is never stored.
 */
class SessionStore(context: Context, private val cipher: TokenCodec = KeystoreTokenCodec) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val _session = MutableStateFlow(readSession())

    val session: StateFlow<Session?> = _session.asStateFlow()

    val lastServer: String
        get() = prefs.getString(KEY_SERVER, null) ?: DEFAULT_SERVER

    fun save(baseUrl: String, token: String, monitorCookie: String? = null) {
        prefs.edit {
            putString(KEY_SERVER, baseUrl)
            putString(KEY_TOKEN, cipher.encrypt(token))
            if (monitorCookie != null) putString(KEY_COOKIE, cipher.encrypt(monitorCookie)) else remove(KEY_COOKIE)
        }
        _session.value = Session(baseUrl, token, monitorCookie)
    }

    fun clear() {
        prefs.edit { remove(KEY_TOKEN); remove(KEY_COOKIE) }
        _session.value = null
    }

    private fun readSession(): Session? {
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val encrypted = prefs.getString(KEY_TOKEN, null) ?: return null
        val token = runCatching { cipher.decrypt(encrypted) }.getOrNull()
        if (token.isNullOrBlank()) {
            prefs.edit { remove(KEY_TOKEN) }
            return null
        }
        val cookie = prefs.getString(KEY_COOKIE, null)?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
        return Session(server, token, cookie)
    }

    companion object {
        const val DEFAULT_SERVER = "https://captain.wardcrew.com"
        private const val KEY_SERVER = "server"
        private const val KEY_TOKEN = "token"
        private const val KEY_COOKIE = "monitor_cookie"
    }
}

interface TokenCodec {
    fun encrypt(plain: String): String
    fun decrypt(encoded: String): String
}

private object KeystoreTokenCodec : TokenCodec {
    private const val ALIAS = "caproverforge_session"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload, 0, 12))
        return String(cipher.doFinal(payload, 12, payload.size - 12), Charsets.UTF_8)
    }
}
