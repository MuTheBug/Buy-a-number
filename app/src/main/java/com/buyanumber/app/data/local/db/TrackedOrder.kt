package com.buyanumber.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.buyanumber.app.domain.model.NumberOrder
import com.buyanumber.app.domain.model.OrderStatus
import java.time.Instant

/**
 * A locally mirrored order.
 *
 * Orders live on 5sim's servers; this table exists so the dashboard renders
 * instantly and offline, and so the background poller knows which numbers to
 * follow and which SMS it has already notified about.
 */
@Entity(tableName = "tracked_orders")
data class TrackedOrder(
    @PrimaryKey val id: Long,
    val phone: String,
    val product: String,
    val country: String?,
    val operator: String?,
    val price: Double,
    val status: String,
    val createdAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
    val smsCount: Int,
    val lastSmsCode: String?,
    val lastSmsText: String?,
    /** How many SMS the user has already been notified about. */
    val notifiedSmsCount: Int = 0,
) {
    fun toDomain(): NumberOrder = NumberOrder(
        id = id,
        phone = phone,
        product = product,
        country = country,
        operator = operator,
        price = price,
        status = OrderStatus.from(status),
        createdAt = createdAtEpochMillis?.let(Instant::ofEpochMilli),
        expiresAt = expiresAtEpochMillis?.let(Instant::ofEpochMilli),
        // The cached row keeps only the newest message; the detail screen
        // fetches the full thread from the API.
        sms = emptyList(),
        forwardingNumber = null,
    )
}

fun NumberOrder.toEntity(notifiedSmsCount: Int = 0): TrackedOrder = TrackedOrder(
    id = id,
    phone = phone,
    product = product,
    country = country,
    operator = operator,
    price = price,
    status = status.apiValue.ifEmpty { OrderStatus.UNKNOWN.name },
    createdAtEpochMillis = createdAt?.toEpochMilli(),
    expiresAtEpochMillis = expiresAt?.toEpochMilli(),
    smsCount = sms.size,
    lastSmsCode = latestSms?.resolvedCode,
    lastSmsText = latestSms?.text,
    notifiedSmsCount = notifiedSmsCount,
)
