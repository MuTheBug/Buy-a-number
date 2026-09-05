package com.buyanumber.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User preferences that shape the buy flow and background polling. */
data class AppSettings(
    val defaultCountry: String? = null,
    val defaultOperator: String = "any",
    val notifyOnSms: Boolean = true,
)

@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            defaultCountry = preferences[DEFAULT_COUNTRY],
            defaultOperator = preferences[DEFAULT_OPERATOR] ?: "any",
            notifyOnSms = preferences[NOTIFY_ON_SMS] ?: true,
        )
    }

    suspend fun setDefaultCountry(country: String?) = dataStore.edit { preferences ->
        if (country == null) preferences.remove(DEFAULT_COUNTRY) else preferences[DEFAULT_COUNTRY] = country
    }

    suspend fun setDefaultOperator(operator: String) = dataStore.edit {
        it[DEFAULT_OPERATOR] = operator
    }

    suspend fun setNotifyOnSms(enabled: Boolean) = dataStore.edit {
        it[NOTIFY_ON_SMS] = enabled
    }

    private companion object {
        val DEFAULT_COUNTRY = stringPreferencesKey("default_country")
        val DEFAULT_OPERATOR = stringPreferencesKey("default_operator")
        val NOTIFY_ON_SMS = booleanPreferencesKey("notify_on_sms")
    }
}
