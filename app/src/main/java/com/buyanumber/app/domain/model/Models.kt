package com.buyanumber.app.domain.model

import java.time.Instant

/** The signed-in 5sim account. */
data class Profile(
    val id: Long,
    val email: String,
    val vendor: String?,
    val balance: Double,
    val frozenBalance: Double,
    val rating: Double,
    val defaultCountry: String?,
    val defaultOperator: String?,
    val forwardingNumber: String?,
)

/**
 * Lifecycle of an order, as reported by 5sim's `status` field.
 *
 * A number is billed while it is [PENDING] or [RECEIVED]; it stops costing
 * money once it reaches a terminal state.
 */
enum class OrderStatus(val apiValue: String, val label: String) {
    PENDING("PENDING", "Waiting for SMS"),
    RECEIVED("RECEIVED", "SMS received"),
    FINISHED("FINISHED", "Finished"),
    CANCELED("CANCELED", "Cancelled"),
    TIMEOUT("TIMEOUT", "Timed out"),
    BANNED("BANNED", "Banned"),
    UNKNOWN("", "Unknown");

    /** True while the number is still live and worth polling. */
    val isActive: Boolean get() = this == PENDING || this == RECEIVED

    companion object {
        fun from(value: String?): OrderStatus =
            entries.firstOrNull { it.apiValue.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** A single SMS delivered to a purchased number. */
data class SmsMessage(
    val sender: String,
    val text: String,
    val code: String?,
    val receivedAt: Instant?,
) {
    /**
     * 5sim extracts the verification code for most services, but not all. When
     * it does not, the first code-shaped run of digits in the body is used.
     */
    val resolvedCode: String? get() = code?.takeIf { it.isNotBlank() } ?: extractCode(text)

    companion object {
        private val CODE_PATTERN = Regex("""\b([0-9]{3}-?[0-9]{3}|[0-9]{4,8})\b""")

        fun extractCode(text: String): String? =
            CODE_PATTERN.find(text)?.value?.replace("-", "")
    }
}

/** A purchased number and everything known about it. */
data class NumberOrder(
    val id: Long,
    val phone: String,
    val product: String,
    val country: String?,
    val operator: String?,
    val price: Double,
    val status: OrderStatus,
    val createdAt: Instant?,
    val expiresAt: Instant?,
    val sms: List<SmsMessage>,
    val forwardingNumber: String?,
) {
    val latestSms: SmsMessage?
        get() = sms.maxByOrNull { it.receivedAt ?: Instant.EPOCH }

    /** The code to show front and centre, if one has arrived. */
    val code: String? get() = latestSms?.resolvedCode

    fun secondsRemaining(now: Instant = Instant.now()): Long {
        val expiry = expiresAt ?: return 0
        return (expiry.epochSecond - now.epochSecond).coerceAtLeast(0)
    }
}

/** A country 5sim sells numbers in. */
data class CountryInfo(
    val code: String,
    val name: String,
    val iso: String,
    val prefix: String,
) {
    /**
     * Regional-indicator flag for the ISO code, so the picker needs no image
     * assets. Falls back to a globe when the code is not two letters.
     */
    val flag: String
        get() {
            val iso2 = iso.uppercase()
            if (iso2.length != 2 || !iso2.all { it in 'A'..'Z' }) return "🌐"
            val base = 0x1F1E6 - 'A'.code
            return String(Character.toChars(base + iso2[0].code)) +
                String(Character.toChars(base + iso2[1].code))
        }
}

/** One buyable combination: a service, in a country, from an operator. */
data class Offer(
    val country: String,
    val product: String,
    val operator: String,
    val price: Double,
    val available: Int,
    val successRate: Double?,
) {
    val inStock: Boolean get() = available > 0
}

/**
 * The best price one country offers for a service, rolled up across its
 * operators. This is the row in the "cheapest country" ranking.
 */
data class CountryOffer(
    val country: CountryInfo,
    /** The operator actually quoting [price] — the one a purchase will use. */
    val bestOperator: String,
    val price: Double,
    /** Numbers available from [bestOperator], not the country total. */
    val available: Int,
    val successRate: Double?,
    /** How many operators sell this service here, for "3 operators" subtext. */
    val operatorCount: Int,
) {
    val inStock: Boolean get() = available > 0
}

/** A service rolled up across operators, for the service picker. */
data class ServiceSummary(
    val product: String,
    val cheapestPrice: Double,
    val available: Int,
)

/** A balance top-up or charge on the account. */
data class Payment(
    val id: Long,
    val type: String,
    val provider: String,
    val amount: Double,
    val balanceAfter: Double,
    val createdAt: Instant?,
)

/** One page of order history plus the total the server reports. */
data class OrdersPage(
    val orders: List<NumberOrder>,
    val total: Int,
)

/** One page of payment history plus the total the server reports. */
data class PaymentsPage(
    val payments: List<Payment>,
    val total: Int,
)
