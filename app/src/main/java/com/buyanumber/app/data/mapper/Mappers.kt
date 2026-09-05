package com.buyanumber.app.data.mapper

import com.buyanumber.app.core.parseApiInstant
import com.buyanumber.app.data.remote.dto.CountryDto
import com.buyanumber.app.data.remote.dto.OrderDto
import com.buyanumber.app.data.remote.dto.PaymentDto
import com.buyanumber.app.data.remote.dto.ProfileDto
import com.buyanumber.app.data.remote.dto.SmsDto
import com.buyanumber.app.domain.model.CountryInfo
import com.buyanumber.app.domain.model.NumberOrder
import com.buyanumber.app.domain.model.OrderStatus
import com.buyanumber.app.domain.model.Payment
import com.buyanumber.app.domain.model.Profile
import com.buyanumber.app.domain.model.SmsMessage

fun ProfileDto.toDomain(): Profile = Profile(
    id = id,
    email = email,
    vendor = vendor?.takeIf { it.isNotBlank() },
    balance = balance,
    frozenBalance = frozenBalance,
    rating = rating,
    defaultCountry = defaultCountry?.name?.takeIf { it.isNotBlank() },
    defaultOperator = defaultOperator?.name?.takeIf { it.isNotBlank() },
    forwardingNumber = defaultForwardingNumber?.takeIf { it.isNotBlank() },
)

fun SmsDto.toDomain(): SmsMessage = SmsMessage(
    sender = sender.orEmpty(),
    text = text.orEmpty(),
    code = code?.takeIf { it.isNotBlank() },
    // `date` is when the SMS reached the number; `created_at` is when 5sim
    // recorded it. The former is what the user cares about.
    receivedAt = parseApiInstant(date) ?: parseApiInstant(createdAt),
)

fun OrderDto.toDomain(): NumberOrder = NumberOrder(
    id = id,
    phone = phone,
    product = product.orEmpty(),
    country = country?.takeIf { it.isNotBlank() },
    operator = operator?.takeIf { it.isNotBlank() },
    price = price,
    status = OrderStatus.from(status),
    createdAt = parseApiInstant(createdAt),
    expiresAt = parseApiInstant(expires),
    sms = sms.orEmpty().map { it.toDomain() }.sortedBy { it.receivedAt },
    forwardingNumber = forwardingNumber?.takeIf { it.isNotBlank() },
)

fun PaymentDto.toDomain(): Payment = Payment(
    id = id,
    type = typeName,
    provider = providerName,
    amount = amount,
    balanceAfter = balance,
    createdAt = parseApiInstant(createdAt),
)

/**
 * `guest/countries` keys each entry by the slug used in buy URLs and nests the
 * ISO code and dialling prefix as single-entry maps (`{"iso": {"RU": 1}}`).
 */
fun CountryDto.toDomain(code: String): CountryInfo = CountryInfo(
    code = code,
    name = textEn.ifBlank { code.replaceFirstChar { it.uppercase() } },
    iso = iso.keys.firstOrNull().orEmpty(),
    prefix = prefix.keys.firstOrNull().orEmpty(),
)
