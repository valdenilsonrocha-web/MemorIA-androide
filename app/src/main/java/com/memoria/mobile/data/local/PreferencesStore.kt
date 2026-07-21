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

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[tokenKey] }

    val baseUrlFlow: Flow<String?> = context.dataStore.data.map { it[baseUrlKey] }

    suspend fun token(): String? = tokenFlow.first()

    suspend fun baseUrl(): String? = baseUrlFlow.first()

    suspend fun setToken(token: String?) {
        context.dataStore.edit { prefs ->
            if (token.isNullOrBlank()) prefs.remove(tokenKey) else prefs[tokenKey] = token
        }
    }

    suspend fun setBaseUrl(url: String?) {
        context.dataStore.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(baseUrlKey) else prefs[baseUrlKey] = url.trim()
        }
    }

    suspend fun clearToken() = setToken(null)
}
