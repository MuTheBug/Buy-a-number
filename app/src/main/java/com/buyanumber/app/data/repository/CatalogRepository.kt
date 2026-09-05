package com.buyanumber.app.data.repository

import com.buyanumber.app.core.apiCall
import com.buyanumber.app.data.mapper.toDomain
import com.buyanumber.app.data.remote.FiveSimApi
import com.buyanumber.app.domain.model.CountryInfo
import com.buyanumber.app.domain.model.Offer
import com.buyanumber.app.domain.model.ServiceSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The public catalog: which countries exist, which services they sell, and what
 * each operator charges. All of it comes from `guest/` endpoints, so it works
 * before the user signs in.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: FiveSimApi,
    private val json: Json,
) {

    private val countriesMutex = Mutex()
    private var cachedCountries: List<CountryInfo>? = null

    /**
     * The country list is effectively static, so it is fetched once per process
     * and reused by the buy flow and the settings screen.
     */
    suspend fun countries(forceRefresh: Boolean = false): Result<List<CountryInfo>> {
        if (!forceRefresh) {
            cachedCountries?.let { return Result.success(it) }
        }
        return countriesMutex.withLock {
            cachedCountries?.takeIf { !forceRefresh }?.let { return Result.success(it) }
            apiCall {
                api.countries()
                    .map { (code, dto) -> dto.toDomain(code) }
                    .sortedBy { it.name.lowercase() }
            }.onSuccess { cachedCountries = it }
        }
    }

    /**
     * Services available in a country. `any` as the operator asks 5sim to roll
     * every operator up into one row per service. Ordering is left to the
     * caller so the user's sort choice never costs a network round trip.
     */
    suspend fun services(country: String): Result<List<ServiceSummary>> = apiCall {
        api.products(country, FiveSimApi.ANY)
            .filter { (_, dto) -> dto.category.equals(ACTIVATION, ignoreCase = true) }
            .map { (product, dto) ->
                ServiceSummary(product = product, cheapestPrice = dto.price, available = dto.quantity)
            }
    }

    /** Per-operator price, stock and success rate for one service in one country. */
    suspend fun offers(country: String, product: String): Result<List<Offer>> = apiCall {
        val tree = api.prices(country = country, product = product)
        flattenPrices(tree, fallbackCountry = country, fallbackProduct = product, json = json)
    }

    private companion object {
        const val ACTIVATION = "activation"
    }
}
