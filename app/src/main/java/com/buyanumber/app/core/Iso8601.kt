package com.buyanumber.app.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 5sim timestamps are ISO-8601 but not uniformly: most carry an offset
 * (`2024-05-01T10:20:30.123456Z`), a few arrive without one, and optional
 * fields can be null or empty. Anything unparseable becomes null rather than
 * throwing, because a missing timestamp must never take down a list of orders.
 */
fun parseApiInstant(value: String?): Instant? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty() || raw.startsWith("0001-01-01")) return null
    return runCatching { OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }
        .getOrNull()
}
