package com.buyanumber.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.buyanumber.app.domain.model.OfferSort
import com.buyanumber.app.domain.model.OrderSort
import com.buyanumber.app.domain.model.ServiceSort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User preferences that shape the buy flow and background polling. */
data class AppSettings(
    val defaultCountry: String? = null,
    val defaultOperator: String = "any",
    val notifyOnSms: Boolean = true,
    val offerSort: OfferSort = OfferSort.DEFAULT,
    val serviceSort: ServiceSort = ServiceSort.DEFAULT,
    val orderSort: OrderSort = OrderSort.DEFAULT,
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
            offerSort = OfferSort.from(preferences[OFFER_SORT]),
            serviceSort = ServiceSort.from(preferences[SERVICE_SORT]),
            orderSort = OrderSort.from(preferences[ORDER_SORT]),
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

    suspend fun setOfferSort(sort: OfferSort) = dataStore.edit { it[OFFER_SORT] = sort.name }

    suspend fun setServiceSort(sort: ServiceSort) = dataStore.edit { it[SERVICE_SORT] = sort.name }

    suspend fun setOrderSort(sort: OrderSort) = dataStore.edit { it[ORDER_SORT] = sort.name }

    private companion object {
        val DEFAULT_COUNTRY = stringPreferencesKey("default_country")
        val DEFAULT_OPERATOR = stringPreferencesKey("default_operator")
        val NOTIFY_ON_SMS = booleanPreferencesKey("notify_on_sms")
        val OFFER_SORT = stringPreferencesKey("offer_sort")
        val SERVICE_SORT = stringPreferencesKey("service_sort")
        val ORDER_SORT = stringPreferencesKey("order_sort")
    }
}
