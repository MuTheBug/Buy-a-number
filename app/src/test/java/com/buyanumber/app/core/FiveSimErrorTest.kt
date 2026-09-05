package com.buyanumber.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FiveSimErrorTest {

    @Test
    fun `maps the documented plain-text errors`() {
        assertEquals(FiveSimError.NoFreePhones, FiveSimError.fromBody("no free phones"))
        assertEquals(FiveSimError.InsufficientBalance, FiveSimError.fromBody("not enough user balance"))
        assertEquals(FiveSimError.InsufficientRating, FiveSimError.fromBody("not enough rating"))
        assertEquals(FiveSimError.OrderNotFound, FiveSimError.fromBody("order not found"))
        assertEquals(FiveSimError.OrderHasSms, FiveSimError.fromBody("order has sms"))
        assertEquals(FiveSimError.OrderNoSms, FiveSimError.fromBody("order no sms"))
        assertEquals(FiveSimError.CancelTooEarly, FiveSimError.fromBody("you need to wait time"))
        assertEquals(FiveSimError.BadSelection, FiveSimError.fromBody("country is incorrect"))
        assertEquals(FiveSimError.ServerError, FiveSimError.fromBody("internal error"))
    }

    @Test
    fun `is not confused by case or surrounding whitespace`() {
        assertEquals(FiveSimError.NoFreePhones, FiveSimError.fromBody("  No Free Phones\n"))
    }

    @Test
    fun `returns null for a body it does not recognise`() {
        assertNull(FiveSimError.fromBody("something entirely new"))
        assertNull(FiveSimError.fromBody(null))
        assertNull(FiveSimError.fromBody("   "))
    }
}
