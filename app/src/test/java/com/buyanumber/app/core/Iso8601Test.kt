package com.buyanumber.app.core

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Iso8601Test {

    @Test
    fun `parses a UTC timestamp with microseconds`() {
        assertEquals(
            Instant.parse("2024-05-01T10:20:30.123456Z"),
            parseApiInstant("2024-05-01T10:20:30.123456Z"),
        )
    }

    @Test
    fun `parses an offset timestamp`() {
        val utc = parseApiInstant("2024-05-01T13:20:30+03:00")
        val same = parseApiInstant("2024-05-01T10:20:30Z")
        assertEquals(same, utc)
    }

    @Test
    fun `parses a timestamp with no offset as UTC`() {
        assertEquals(parseApiInstant("2024-05-01T10:20:30Z"), parseApiInstant("2024-05-01T10:20:30"))
    }

    @Test
    fun `treats Go's zero time as absent`() {
        assertNull(parseApiInstant("0001-01-01T00:00:00Z"))
    }

    @Test
    fun `returns null rather than throwing on junk`() {
        assertNull(parseApiInstant(null))
        assertNull(parseApiInstant(""))
        assertNull(parseApiInstant("not a date"))
    }
}
