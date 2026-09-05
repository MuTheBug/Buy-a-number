package com.buyanumber.app.data.repository

import com.buyanumber.app.core.apiCall
import com.buyanumber.app.data.mapper.toDomain
import com.buyanumber.app.data.remote.FiveSimApi
import com.buyanumber.app.domain.model.CountryInfo
import com.buyanumber.app.domain.model.CountryOffer
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

    /**
     * Every service 5sim sells anywhere, for the service-first flow where no
     * country has been chosen yet. `any/any` asks for the global catalog.
     */
    suspend fun allServices(): Result<List<ServiceSummary>> = apiCall {
        api.products(FiveSimApi.ANY, FiveSimApi.ANY)
            .filter { (_, dto) -> dto.category.equals(ACTIVATION, ignoreCase = true) }
            .map { (product, dto) ->
                ServiceSummary(product = product, cheapestPrice = dto.price, available = dto.quantity)
            }
    }

    /**
     * Ranks every country by what it charges for one service.
     *
     * Filtering `guest/prices` by product alone returns the whole
     * country -> operator tree for that service, which is the one call that
     * answers "where is WhatsApp cheapest right now". Each country is reduced
     * to the operator actually quoting the lowest in-stock price, since that is
     * the one a purchase would use.
     */
    suspend fun cheapestByCountry(product: String): Result<List<CountryOffer>> = apiCall {
        val tree = api.prices(product = product)
        val offers = flattenPrices(tree, fallbackCountry = null, fallbackProduct = product, json = json)

        // Country metadata is a separate endpoint; without it the ranking would
        // show raw slugs.
        val known = countries().getOrNull().orEmpty().associateBy { it.code }
        rankCountriesByPrice(offers, known)
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
