package com.buyanumber.app.domain

import com.buyanumber.app.domain.model.Offer
import com.buyanumber.app.domain.model.OfferSort
import com.buyanumber.app.domain.model.OrderSort
import com.buyanumber.app.domain.model.ServiceSort
import com.buyanumber.app.domain.model.ServiceSummary
import com.buyanumber.app.domain.model.sortedBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortingTest {

    private fun offer(
        operator: String,
        price: Double,
        available: Int = 10,
        rate: Double? = null,
    ) = Offer(
        country = "russia",
        product = "telegram",
        operator = operator,
        price = price,
        available = available,
        successRate = rate,
    )

    private val offers = listOf(
        offer("mts", price = 20.0, available = 5, rate = 95.0),
        offer("beeline", price = 10.0, available = 50, rate = 60.0),
        offer("tele2", price = 30.0, available = 1, rate = 80.0),
    )

    @Test
    fun `sorts offers cheapest first`() {
        assertEquals(
            listOf("beeline", "mts", "tele2"),
            offers.sortedBy(OfferSort.PRICE_LOW).map { it.operator },
        )
    }

    @Test
    fun `sorts offers most expensive first`() {
        assertEquals(
            listOf("tele2", "mts", "beeline"),
            offers.sortedBy(OfferSort.PRICE_HIGH).map { it.operator },
        )
    }

    @Test
    fun `sorts offers by remaining stock`() {
        assertEquals(
            listOf("beeline", "mts", "tele2"),
            offers.sortedBy(OfferSort.AVAILABILITY).map { it.operator },
        )
    }

    @Test
    fun `sorts offers by success rate`() {
        assertEquals(
            listOf("mts", "tele2", "beeline"),
            offers.sortedBy(OfferSort.SUCCESS_RATE).map { it.operator },
        )
    }

    @Test
    fun `keeps sold-out offers at the bottom whatever the sort`() {
        val withSoldOut = offers + offer("soldout", price = 0.5, available = 0, rate = 100.0)

        OfferSort.entries.forEach { sort ->
            assertEquals(
                "sold-out operator should sink under $sort",
                "soldout",
                withSoldOut.sortedBy(sort).last().operator,
            )
        }
    }

    @Test
    fun `ranks operators with no reported success rate last`() {
        val withUnknown = listOf(
            offer("unknown", price = 1.0, rate = null),
            offer("known", price = 99.0, rate = 10.0),
        )

        assertEquals(
            listOf("known", "unknown"),
            withUnknown.sortedBy(OfferSort.SUCCESS_RATE).map { it.operator },
        )
    }

    @Test
    fun `breaks price ties by operator name so the order is stable`() {
        val tied = listOf(
            offer("zeta", price = 5.0),
            offer("alpha", price = 5.0),
        )

        assertEquals(
            listOf("alpha", "zeta"),
            tied.sortedBy(OfferSort.PRICE_LOW).map { it.operator },
        )
    }

    private val services = listOf(
        ServiceSummary(product = "whatsapp", cheapestPrice = 30.0, available = 5),
        ServiceSummary(product = "telegram", cheapestPrice = 10.0, available = 100),
        ServiceSummary(product = "amazon", cheapestPrice = 20.0, available = 50),
    )

    @Test
    fun `sorts services alphabetically`() {
        assertEquals(
            listOf("amazon", "telegram", "whatsapp"),
            services.sortedBy(ServiceSort.NAME).map { it.product },
        )
    }

    @Test
    fun `sorts services cheapest first`() {
        assertEquals(
            listOf("telegram", "amazon", "whatsapp"),
            services.sortedBy(ServiceSort.PRICE_LOW).map { it.product },
        )
    }

    @Test
    fun `sorts services most expensive first`() {
        assertEquals(
            listOf("whatsapp", "amazon", "telegram"),
            services.sortedBy(ServiceSort.PRICE_HIGH).map { it.product },
        )
    }

    @Test
    fun `sorts services by remaining stock`() {
        assertEquals(
            listOf("telegram", "amazon", "whatsapp"),
            services.sortedBy(ServiceSort.AVAILABILITY).map { it.product },
        )
    }

    @Test
    fun `keeps sold-out services at the bottom whatever the sort`() {
        val withSoldOut = services + ServiceSummary("aaa-soldout", cheapestPrice = 0.1, available = 0)

        ServiceSort.entries.forEach { sort ->
            assertEquals(
                "sold-out service should sink under $sort",
                "aaa-soldout",
                withSoldOut.sortedBy(sort).last().product,
            )
        }
    }

    @Test
    fun `order sorts map onto the API's own paging parameters`() {
        assertEquals("id", OrderSort.NEWEST.apiField)
        assertTrue(OrderSort.NEWEST.reverse)
        assertEquals("id", OrderSort.OLDEST.apiField)
        assertTrue(!OrderSort.OLDEST.reverse)
    }

    @Test
    fun `falls back to the default when a stored sort no longer exists`() {
        assertEquals(OfferSort.DEFAULT, OfferSort.from("REMOVED_OPTION"))
        assertEquals(OfferSort.DEFAULT, OfferSort.from(null))
        assertEquals(OfferSort.PRICE_HIGH, OfferSort.from("PRICE_HIGH"))
        assertEquals(ServiceSort.DEFAULT, ServiceSort.from("nope"))
        assertEquals(OrderSort.DEFAULT, OrderSort.from("nope"))
    }
}
