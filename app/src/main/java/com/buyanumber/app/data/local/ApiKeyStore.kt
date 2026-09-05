package com.buyanumber.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Holds the 5sim API key, encrypted at rest by [KeystoreCipher].
 *
 * The decrypted key is cached in memory because [com.buyanumber.app.data.remote.AuthInterceptor]
 * reads it on every request from OkHttp's blocking dispatcher.
 */
@Singleton
class ApiKeyStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: KeystoreCipher,
) {

    @Volatile
    private var cached: String? = null

    /** Emits the key, or null when the user is signed out. */
    val apiKey: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY]?.let(cipher::decrypt).also { cached = it }
    }

    val isSignedIn: Flow<Boolean> = apiKey.map { !it.isNullOrBlank() }

    suspend fun currentKey(): String? = cached ?: apiKey.first()

    suspend fun save(apiKey: String) {
        val trimmed = apiKey.trim()
        cached = trimmed
        dataStore.edit { it[KEY] = cipher.encrypt(trimmed) }
    }

    suspend fun clear() {
        cached = null
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("api_key_sealed")
    }
}
