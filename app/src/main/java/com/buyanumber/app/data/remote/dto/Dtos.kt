package com.buyanumber.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the 5sim REST API (https://5sim.net/docs).
 *
 * 5sim mixes naming conventions between endpoints — `/user/profile` and
 * `/user/check` use snake_case while the paginated history endpoints use
 * PascalCase — so every field is mapped explicitly.
 */

@Serializable
data class ProfileDto(
    val id: Long = 0,
    val email: String = "",
    val vendor: String? = null,
    val balance: Double = 0.0,
    @SerialName("frozen_balance") val frozenBalance: Double = 0.0,
    val rating: Double = 0.0,
    @SerialName("default_country") val defaultCountry: DefaultCountryDto? = null,
    @SerialName("default_operator") val defaultOperator: DefaultOperatorDto? = null,
    @SerialName("default_forwarding_number") val defaultForwardingNumber: String? = null,
)

@Serializable
data class DefaultCountryDto(
    val name: String = "",
    val iso: String = "",
    val prefix: String = "",
)

@Serializable
data class DefaultOperatorDto(
    val name: String = "",
)

@Serializable
data class OrderDto(
    val id: Long = 0,
    val phone: String = "",
    val operator: String? = null,
    val product: String? = null,
    val price: Double = 0.0,
    val status: String? = null,
    val expires: String? = null,
    val sms: List<SmsDto>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val country: String? = null,
    val forwarding: Boolean? = null,
    @SerialName("forwarding_number") val forwardingNumber: String? = null,
)

@Serializable
data class SmsDto(
    @SerialName("created_at") val createdAt: String? = null,
    val date: String? = null,
    val sender: String? = null,
    val text: String? = null,
    val code: String? = null,
    @SerialName("is_wave") val isWave: Boolean? = null,
    @SerialName("wave_uuid") val waveUuid: String? = null,
)

@Serializable
data class OrdersHistoryDto(
    @SerialName("Data") val data: List<OrderDto> = emptyList(),
    @SerialName("ProductNames") val productNames: List<String> = emptyList(),
    @SerialName("Statuses") val statuses: List<String> = emptyList(),
    @SerialName("Total") val total: Int = 0,
)

@Serializable
data class SmsInboxDto(
    @SerialName("Data") val data: List<SmsDto> = emptyList(),
    @SerialName("Total") val total: Int = 0,
)

@Serializable
data class PaymentsHistoryDto(
    @SerialName("Data") val data: List<PaymentDto> = emptyList(),
    @SerialName("PaymentTypes") val paymentTypes: List<NameDto> = emptyList(),
    @SerialName("PaymentProviders") val paymentProviders: List<NameDto> = emptyList(),
    @SerialName("PaymentStatuses") val paymentStatuses: List<NameDto> = emptyList(),
    @SerialName("Total") val total: Int = 0,
)

@Serializable
data class NameDto(
    @SerialName("Name") val name: String = "",
)

@Serializable
data class PaymentDto(
    @SerialName("ID") val id: Long = 0,
    @SerialName("TypeName") val typeName: String = "",
    @SerialName("ProviderName") val providerName: String = "",
    @SerialName("Amount") val amount: Double = 0.0,
    @SerialName("Balance") val balance: Double = 0.0,
    @SerialName("CreatedAt") val createdAt: String? = null,
)

/**
 * One entry of `GET /v1/guest/countries`. `iso` and `prefix` are objects keyed
 * by the value itself (`{"iso": {"RU": 1}}`), so they are decoded as maps and
 * the first key is taken.
 */
@Serializable
data class CountryDto(
    val iso: Map<String, Int> = emptyMap(),
    val prefix: Map<String, Int> = emptyMap(),
    @SerialName("text_en") val textEn: String = "",
    @SerialName("text_ru") val textRu: String = "",
)

/** One entry of `GET /v1/guest/products/{country}/{operator}`. */
@Serializable
data class ProductDto(
    @SerialName("Category") val category: String = "",
    @SerialName("Qty") val quantity: Int = 0,
    @SerialName("Price") val price: Double = 0.0,
)

/**
 * The leaf of the `GET /v1/guest/prices` tree: country -> product -> operator.
 * `rate` is the recent delivery success rate as a percentage, and is absent for
 * operators that have not sold recently.
 */
@Serializable
data class PriceDto(
    val cost: Double = 0.0,
    val count: Int = 0,
    val rate: Double? = null,
)
