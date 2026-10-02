package com.memoria.mobile.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "memoria_prefs")

/**
 * Persists the JWT and the (overridable) backend base URL. The token is what the
 * web frontend keeps in localStorage under `memoria_auth_token`; here it lives in
 * a DataStore excluded from cloud backup (see backup_rules.xml).
 *
 * The JWT is encrypted at rest with AES-256-GCM using a key held in the Android
 * Keystore — the same approach [CredentialStore] uses for passwords. A rooted
 * device or an ADB backup can still read the DataStore file, but the token blob
 * is opaque without the hardware-backed key.
 */
class PreferencesStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("auth_token")
    private val baseUrlKey = stringPreferencesKey("api_base_url")

    /**
     * The shipped default that was in effect when the override was saved. It lets
     * the repository drop an override that a newer build has outgrown — see
     * `MemoriaRepository.bootstrap`.
     */
    private val baseUrlStampKey = stringPreferencesKey("api_base_url_stamp")

    val tokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[tokenKey]?.let { blob -> TokenCipher.decrypt(blob) }
    }

    val baseUrlFlow: Flow<String?> = context.dataStore.data.map { it[baseUrlKey] }

    suspend fun token(): String? = tokenFlow.first()

    suspend fun baseUrl(): String? = baseUrlFlow.first()

    suspend fun baseUrlStamp(): String? = context.dataStore.data.map { it[baseUrlStampKey] }.first()

    suspend fun setToken(token: String?) {
        context.dataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(tokenKey)
            } else {
                val encrypted = TokenCipher.encrypt(token)
                if (encrypted != null) {
                    prefs[tokenKey] = encrypted
                } else {
                    // Keystore refused — store nothing rather than plaintext.
                    prefs.remove(tokenKey)
                }
            }
        }
    }

    suspend fun setBaseUrl(url: String?, stamp: String? = null) {
        context.dataStore.edit { prefs ->
            if (url.isNullOrBlank()) {
                prefs.remove(baseUrlKey)
                prefs.remove(baseUrlStampKey)
            } else {
                prefs[baseUrlKey] = url.trim()
                if (stamp.isNullOrBlank()) prefs.remove(baseUrlStampKey) else prefs[baseUrlStampKey] = stamp
            }
        }
    }

    // ---- Generic JSON slots (see LocalStore) ----

    fun stringFlow(name: String): Flow<String?> {
        val key = stringPreferencesKey(name)
        return context.dataStore.data.map { it[key] }
    }

    suspend fun string(name: String): String? = stringFlow(name).first()

    suspend fun setString(name: String, value: String?) {
        val key = stringPreferencesKey(name)
        context.dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(key) else prefs[key] = value
        }
    }

    suspend fun clearToken() = setToken(null)

    suspend fun clearBaseUrl() = setBaseUrl(null)

    // ---- AES-256-GCM cipher for the session token ----

    /**
     * Mirrors the crypto in [CredentialStore] but uses a separate Keystore alias
     * so revoking the credential key does not kill the session and vice-versa.
     */
    private object TokenCipher {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "memoria_session_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val BLOB_SEPARATOR = ':'

        fun encrypt(plain: String): String? = runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            "${cipher.iv.toBase64()}$BLOB_SEPARATOR${cipherText.toBase64()}"
        }.getOrNull()

        fun decrypt(blob: String): String? = runCatching {
            val iv = blob.substringBefore(BLOB_SEPARATOR).fromBase64()
            val cipherText = blob.substringAfter(BLOB_SEPARATOR).fromBase64()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrNull()

        private fun secretKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
                return it.secretKey
            }
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return generator.generateKey()
        }

        private fun ByteArray.toBase64(): String =
            Base64.encodeToString(this, Base64.NO_WRAP)

        private fun String.fromBase64(): ByteArray =
            Base64.decode(this, Base64.NO_WRAP)
    }
}
