package com.buyanumber.app.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 5sim prices and balances are quoted in rubles. The symbol is appended rather
 * than run through the platform currency formatter so it reads the same on
 * every locale the app might run in.
 */
fun formatMoney(amount: Double): String =
    String.format(Locale.US, "%.2f ₽", amount)

fun formatRate(rate: Double?): String? =
    rate?.let { String.format(Locale.US, "%.0f%%", it) }

/** `04:59` style countdown for an order's remaining life. */
fun formatCountdown(totalSeconds: Long): String {
    val seconds = abs(totalSeconds)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, remainder)
    }
}

private val DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())

fun formatDateTime(instant: Instant?): String =
    instant?.atZone(ZoneId.systemDefault())?.format(DATE_TIME) ?: "—"

/** "just now" / "4m ago" / "2h ago" for SMS timestamps. */
fun formatRelative(instant: Instant?, now: Instant = Instant.now()): String {
    if (instant == null) return "—"
    val seconds = now.epochSecond - instant.epochSecond
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        else -> formatDateTime(instant)
    }
}

/** Turns a 5sim slug such as `whatsapp` into `Whatsapp` for display. */
fun String.toDisplayName(): String =
    split('_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        .ifBlank { this }
