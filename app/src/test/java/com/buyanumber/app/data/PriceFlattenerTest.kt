package com.buyanumber.app.data

import com.buyanumber.app.data.repository.flattenPrices
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `guest/prices` changes shape with the filters sent, and the leaves are the
 * only thing that stays put. These cases pin down each shape 5sim returns.
 */
class PriceFlattenerTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(raw: String): JsonObject = json.decodeFromString(JsonObject.serializer(), raw)

    @Test
    fun `reads the full country to product to operator tree`() {
        val tree = parse(
            """
            {
              "russia": {
                "telegram": {
                  "beeline": {"cost": 12.5, "count": 40, "rate": 91.2},
                  "mts":     {"cost": 15.0, "count": 0,  "rate": 55.0}
                }
              }
            }
            """.trimIndent(),
        )

        val offers = flattenPrices(tree, fallbackCountry = null, fallbackProduct = null, json = json)

        assertEquals(2, offers.size)
        val beeline = offers.single { it.operator == "beeline" }
        assertEquals("russia", beeline.country)
        assertEquals("telegram", beeline.product)
        assertEquals(12.5, beeline.price, 0.001)
        assertEquals(40, beeline.available)
        assertEquals(91.2, beeline.successRate!!, 0.001)
        assertTrue(beeline.inStock)

        assertTrue(offers.none { it.operator == "mts" && it.inStock })
    }

    @Test
    fun `fills in the country when the response omits that level`() {
        val tree = parse("""{"telegram": {"beeline": {"cost": 9.0, "count": 3}}}""")

        val offers = flattenPrices(tree, fallbackCountry = "russia", fallbackProduct = null, json = json)

        val offer = offers.single()
        assertEquals("russia", offer.country)
        assertEquals("telegram", offer.product)
        assertEquals("beeline", offer.operator)
    }

    @Test
    fun `fills in both levels when only operators come back`() {
        val tree = parse("""{"beeline": {"cost": 9.0, "count": 3}}""")

        val offers = flattenPrices(tree, fallbackCountry = "russia", fallbackProduct = "telegram", json = json)

        val offer = offers.single()
        assertEquals("russia", offer.country)
        assertEquals("telegram", offer.product)
        assertEquals("beeline", offer.operator)
    }

    @Test
    fun `keeps operators whose success rate is missing`() {
        val tree = parse("""{"russia": {"telegram": {"beeline": {"cost": 9.0, "count": 3}}}}""")

        val offer = flattenPrices(tree, null, null, json).single()

        assertNull(offer.successRate)
    }

    @Test
    fun `walks every country and product in an unfiltered response`() {
        val tree = parse(
            """
            {
              "russia": {"telegram": {"mts": {"cost": 1.0, "count": 1}}},
              "usa":    {"telegram": {"virtual21": {"cost": 2.0, "count": 2}},
                         "whatsapp": {"virtual21": {"cost": 3.0, "count": 3}}}
            }
            """.trimIndent(),
        )

        val offers = flattenPrices(tree, null, null, json)

        assertEquals(3, offers.size)
        assertEquals(setOf("russia", "usa"), offers.map { it.country }.toSet())
        assertEquals(setOf("telegram", "whatsapp"), offers.map { it.product }.toSet())
    }

    @Test
    fun `returns nothing for an empty response`() {
        assertTrue(flattenPrices(parse("{}"), "russia", "telegram", json).isEmpty())
    }

    @Test
    fun `skips branches that are not price leaves`() {
        val tree = parse("""{"russia": {"telegram": {"note": "unavailable"}}}""")

        assertTrue(flattenPrices(tree, null, null, json).isEmpty())
    }
}
