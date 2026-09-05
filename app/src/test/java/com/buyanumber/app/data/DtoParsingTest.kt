package com.buyanumber.app.data

import com.buyanumber.app.data.mapper.toDomain
import com.buyanumber.app.data.remote.dto.CountryDto
import com.buyanumber.app.data.remote.dto.OrderDto
import com.buyanumber.app.data.remote.dto.OrdersHistoryDto
import com.buyanumber.app.data.remote.dto.ProfileDto
import com.buyanumber.app.domain.model.OrderStatus
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses the response shapes documented at https://5sim.net/docs. */
class DtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `parses a profile`() {
        val profile = json.decodeFromString(
            ProfileDto.serializer(),
            """
            {
              "id": 1,
              "email": "user@example.com",
              "vendor": "demo",
              "default_forwarding_number": "79000000000",
              "balance": 100.5,
              "rating": 96.5,
              "default_country": {"name": "russia", "iso": "ru", "prefix": "+7"},
              "default_operator": {"name": "any"},
              "frozen_balance": 20.0
            }
            """.trimIndent(),
        ).toDomain()

        assertEquals("user@example.com", profile.email)
        assertEquals(100.5, profile.balance, 0.001)
        assertEquals(20.0, profile.frozenBalance, 0.001)
        assertEquals("russia", profile.defaultCountry)
        assertEquals("any", profile.defaultOperator)
        assertEquals("demo", profile.vendor)
    }

    @Test
    fun `parses a freshly bought order that has no SMS yet`() {
        val order = json.decodeFromString(
            OrderDto.serializer(),
            """
            {
              "id": 11631253,
              "phone": "+79000381454",
              "operator": "beeline",
              "product": "vkontakte",
              "price": 3,
              "status": "PENDING",
              "expires": "2020-06-28T16:32:43.307041Z",
              "sms": null,
              "created_at": "2020-06-28T16:17:43.307041Z",
              "forwarding": false,
              "forwarding_number": "",
              "country": "russia"
            }
            """.trimIndent(),
        ).toDomain()

        assertEquals(11631253L, order.id)
        assertEquals("+79000381454", order.phone)
        assertEquals(OrderStatus.PENDING, order.status)
        assertTrue(order.status.isActive)
        assertTrue(order.sms.isEmpty())
        assertNull(order.code)
        assertNull(order.forwardingNumber)
        assertEquals(900L, order.expiresAt!!.epochSecond - order.createdAt!!.epochSecond)
    }

    @Test
    fun `parses an order carrying a verification code`() {
        val order = json.decodeFromString(
            OrderDto.serializer(),
            """
            {
              "id": 11631253,
              "phone": "+79000381454",
              "product": "vkontakte",
              "price": 3,
              "status": "RECEIVED",
              "expires": "2020-06-28T16:32:43.307041Z",
              "created_at": "2020-06-28T16:17:43.307041Z",
              "sms": [
                {
                  "created_at": "2020-06-28T16:18:00Z",
                  "date": "2020-06-28T16:18:00Z",
                  "sender": "VKcom",
                  "text": "VK: 34141 - your code",
                  "code": "34141"
                }
              ]
            }
            """.trimIndent(),
        ).toDomain()

        assertEquals(OrderStatus.RECEIVED, order.status)
        assertEquals(1, order.sms.size)
        assertEquals("34141", order.code)
        assertEquals("VKcom", order.latestSms!!.sender)
    }

    @Test
    fun `orders the SMS thread oldest first and reads the newest code`() {
        val order = json.decodeFromString(
            OrderDto.serializer(),
            """
            {
              "id": 1, "phone": "+1", "product": "telegram", "price": 1, "status": "RECEIVED",
              "sms": [
                {"date": "2020-06-28T16:20:00Z", "sender": "b", "text": "second 22222", "code": "22222"},
                {"date": "2020-06-28T16:18:00Z", "sender": "a", "text": "first 11111",  "code": "11111"}
              ]
            }
            """.trimIndent(),
        ).toDomain()

        assertEquals(listOf("11111", "22222"), order.sms.map { it.code })
        assertEquals("22222", order.code)
    }

    @Test
    fun `parses the paginated order history`() {
        val page = json.decodeFromString(
            OrdersHistoryDto.serializer(),
            """
            {
              "Data": [
                {"id": 1, "phone": "+7900", "product": "telegram", "price": 5, "status": "FINISHED"}
              ],
              "ProductNames": ["telegram"],
              "Statuses": ["FINISHED"],
              "Total": 137
            }
            """.trimIndent(),
        )

        assertEquals(137, page.total)
        assertEquals(OrderStatus.FINISHED, page.data.single().toDomain().status)
    }

    @Test
    fun `unpacks the single-entry iso and prefix maps in the country list`() {
        val countries = json.decodeFromString(
            MapSerializer(String.serializer(), CountryDto.serializer()),
            """
            {"russia": {"iso": {"RU": 1}, "prefix": {"+7": 1}, "text_en": "Russia", "text_ru": "Россия"}}
            """.trimIndent(),
        )

        val russia = countries.getValue("russia").toDomain("russia")
        assertEquals("Russia", russia.name)
        assertEquals("RU", russia.iso)
        assertEquals("+7", russia.prefix)
        assertEquals("🇷🇺", russia.flag)
    }

    @Test
    fun `falls back to a globe when the ISO code is unusable`() {
        val unknown = CountryDto(textEn = "Nowhere").toDomain("nowhere")
        assertEquals("🌐", unknown.flag)
    }

    @Test
    fun `treats an unrecognised status as unknown rather than failing`() {
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.from("SOMETHING_NEW"))
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.from(null))
        assertEquals(OrderStatus.PENDING, OrderStatus.from("pending"))
    }
}
