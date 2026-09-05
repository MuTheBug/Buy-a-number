package com.buyanumber.app.data.repository

import com.buyanumber.app.domain.model.CountryInfo
import com.buyanumber.app.domain.model.CountryOffer
import com.buyanumber.app.domain.model.Offer

/**
 * Reduces the flattened price rows for one service to a single row per country:
 * the operator actually quoting the lowest in-stock price, which is the one a
 * purchase from the ranking will use.
 *
 * Countries with nothing in stock are dropped rather than listed at a price
 * nobody can pay — 5sim keeps quoting sold-out operators, and a country showing
 * 3 ₽ with zero numbers would otherwise sit permanently at the top.
 *
 * [countryLookup] supplies display names and flags; a country 5sim prices but
 * does not describe falls back to its slug rather than disappearing.
 */
internal fun rankCountriesByPrice(
    offers: List<Offer>,
    countryLookup: Map<String, CountryInfo>,
): List<CountryOffer> = offers
    .filter { it.country.isNotBlank() }
    .groupBy { it.country }
    .mapNotNull { (code, countryOffers) ->
        val inStock = countryOffers.filter { it.inStock }
        val best = inStock.minByOrNull { it.price } ?: return@mapNotNull null
        CountryOffer(
            country = countryLookup[code] ?: CountryInfo(
                code = code,
                name = code.replaceFirstChar { it.uppercase() },
                iso = "",
                prefix = "",
            ),
            bestOperator = best.operator,
            price = best.price,
            available = best.available,
            successRate = best.successRate,
            operatorCount = inStock.size,
        )
    }
