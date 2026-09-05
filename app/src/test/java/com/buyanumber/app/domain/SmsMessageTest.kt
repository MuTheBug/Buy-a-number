package com.buyanumber.app.domain

import com.buyanumber.app.domain.model.SmsMessage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsMessageTest {

    private fun sms(text: String, code: String? = null) = SmsMessage(
        sender = "Telegram",
        text = text,
        code = code,
        receivedAt = Instant.EPOCH,
    )

    @Test
    fun `prefers the code 5sim extracted`() {
        val message = sms(text = "Your login code is 123456", code = "999999")
        assertEquals("999999", message.resolvedCode)
    }

    @Test
    fun `falls back to the code in the body when 5sim did not extract one`() {
        assertEquals("123456", sms("Your login code is 123456").resolvedCode)
    }

    @Test
    fun `treats a blank extracted code as missing`() {
        assertEquals("4821", sms(text = "Code: 4821", code = "  ").resolvedCode)
    }

    @Test
    fun `strips the separator from hyphenated codes`() {
        assertEquals("123456", sms("G-123-456 is your code").resolvedCode)
    }

    @Test
    fun `ignores runs of digits that are too short to be a code`() {
        assertNull(sms("Hi 12, welcome").resolvedCode)
    }

    @Test
    fun `ignores runs of digits that are too long to be a code`() {
        assertNull(sms("Order 1234567890123 shipped").resolvedCode)
    }
}
