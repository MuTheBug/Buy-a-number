package com.buyanumber.app.data

import com.buyanumber.app.data.repository.flattenPrices
import com.buyanumber.app.data.repository.rankCountriesByPrice
import com.buyanumber.app.domain.model.CountryInfo
import com.buyanumber.app.domain.model.CountryOffer
import com.buyanumber.app.domain.model.OfferSort
import com.buyanumber.app.domain.model.sortedBy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cheapest-country ranking reduces `guest/prices?product=X` to one row per
 * country. These tests pin the reduction: which operator represents a country,
 * and which countries are dropped entirely.
 */
class CheapestCountryTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(raw: String): JsonObject = json.decodeFromString(JsonObject.serializer(), raw)

    /**
     * Exercises the same flatten-then-rank pair the repository calls; only the
     * Retrofit hop and the country-name lookup are left out.
     */
    private fun rank(tree: JsonObject, product: String): List<CountryOffer> =
        rankCountriesByPrice(
            offers = flattenPrices(tree, fallbackCountry = null, fallbackProduct = product, json = json),
            countryLookup = emptyMap(),
        ).sortedBy(OfferSort.PRICE_LOW)

    private val whatsappPrices = parse(
        """
        {
          "russia":    {"whatsapp": {"beeline": {"cost": 25.0, "count": 40, "rate": 90.0},
                                     "mts":     {"cost": 18.0, "count": 12, "rate": 70.0}}},
          "indonesia": {"whatsapp": {"virtual21": {"cost": 8.5, "count": 300, "rate": 85.0}}},
          "vietnam":   {"whatsapp": {"viettel": {"cost": 12.0, "count": 5}}},
          "usa":       {"whatsapp": {"virtual38": {"cost": 3.0, "count": 0, "rate": 99.0}}}
        }
        """.trimIndent(),
    )

    @Test
    fun `ranks countries cheapest first`() {
        val ranked = rank(whatsappPrices, "whatsapp")

        assertEquals(listOf("indonesia", "vietnam", "russia"), ranked.map { it.country.code })
        // With no country metadata the slug is title-cased for display.
        assertEquals("Indonesia", ranked.first().country.name)
    }

    @Test
    fun `represents each country by its cheapest in-stock operator`() {
        val russia = rank(whatsappPrices, "whatsapp").single { it.country.code == "russia" }

        assertEquals("mts", russia.bestOperator)
        assertEquals(18.0, russia.price, 0.001)
        assertEquals(12, russia.available)
        assertEquals(2, russia.operatorCount)
    }

    @Test
    fun `drops countries with nothing in stock, however cheap they look`() {
        val ranked = rank(whatsappPrices, "whatsapp")

        // usa quotes 3.00 but has zero numbers, so it is not a buyable option.
        assertTrue(ranked.none { it.country.code == "usa" })
    }

    @Test
    fun `ignores a sold-out operator when picking a country's representative`() {
        val tree = parse(
            """
            {"kenya": {"whatsapp": {"cheap_soldout": {"cost": 1.0, "count": 0},
                                    "real":          {"cost": 40.0, "count": 7}}}}
            """.trimIndent(),
        )

        val kenya = rank(tree, "whatsapp").single()

        assertEquals("real", kenya.bestOperator)
        assertEquals(40.0, kenya.price, 0.001)
        assertEquals(1, kenya.operatorCount)
    }

    @Test
    fun `carries the success rate of the operator that will actually be bought`() {
        val indonesia = rank(whatsappPrices, "whatsapp").single { it.country.code == "indonesia" }
        assertEquals(85.0, indonesia.successRate!!, 0.001)

        val vietnam = rank(whatsappPrices, "whatsapp").single { it.country.code == "vietnam" }
        assertNull(vietnam.successRate)
    }

    @Test
    fun `re-sorting the ranking keeps sold-out rows out of the way`() {
        val ranked = rank(whatsappPrices, "whatsapp")

        assertEquals(
            listOf("russia", "vietnam", "indonesia"),
            ranked.sortedBy(OfferSort.PRICE_HIGH).map { it.country.code },
        )
        assertEquals(
            listOf("indonesia", "russia", "vietnam"),
            ranked.sortedBy(OfferSort.AVAILABILITY).map { it.country.code },
        )
        assertEquals(
            listOf("indonesia", "russia", "vietnam"),
            ranked.sortedBy(OfferSort.SUCCESS_RATE).map { it.country.code },
        )
    }

    @Test
    fun `returns nothing when the service is sold out everywhere`() {
        val tree = parse("""{"russia": {"whatsapp": {"mts": {"cost": 5.0, "count": 0}}}}""")

        assertTrue(rank(tree, "whatsapp").isEmpty())
    }
}
