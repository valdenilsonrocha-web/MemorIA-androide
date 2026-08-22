package com.memoria.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "memoria_prefs")

/**
 * Persists the JWT and the (overridable) backend base URL. The token is what the
 * web frontend keeps in localStorage under `memoria_auth_token`; here it lives in
 * a DataStore excluded from cloud backup (see backup_rules.xml).
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

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[tokenKey] }

    val baseUrlFlow: Flow<String?> = context.dataStore.data.map { it[baseUrlKey] }

    suspend fun token(): String? = tokenFlow.first()

    suspend fun baseUrl(): String? = baseUrlFlow.first()

    suspend fun baseUrlStamp(): String? = context.dataStore.data.map { it[baseUrlStampKey] }.first()

    suspend fun setToken(token: String?) {
        context.dataStore.edit { prefs ->
            if (token.isNullOrBlank()) prefs.remove(tokenKey) else prefs[tokenKey] = token
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
}
