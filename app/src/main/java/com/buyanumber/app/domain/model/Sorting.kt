package com.buyanumber.app.domain.model

/**
 * User-selectable orderings for the buy flow and the order list.
 *
 * Out-of-stock rows always sink to the bottom regardless of the choice — a
 * sold-out operator at a great price is still not something you can buy — so
 * every comparator here is applied *after* an availability check.
 */
enum class OfferSort(val label: String) {
    PRICE_LOW("Price: low to high"),
    PRICE_HIGH("Price: high to low"),
    AVAILABILITY("Most numbers left"),
    SUCCESS_RATE("Best success rate");

    companion object {
        val DEFAULT = PRICE_LOW

        fun from(name: String?): OfferSort =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

enum class ServiceSort(val label: String) {
    NAME("Name: A to Z"),
    PRICE_LOW("Cheapest first"),
    PRICE_HIGH("Most expensive first"),
    AVAILABILITY("Most numbers left");

    companion object {
        val DEFAULT = NAME

        fun from(name: String?): ServiceSort =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Order history is paginated server-side, so these map onto 5sim's own `order`
 * and `reverse` query parameters rather than sorting a partial page locally.
 */
enum class OrderSort(val label: String, val apiField: String, val reverse: Boolean) {
    NEWEST("Newest first", "id", true),
    OLDEST("Oldest first", "id", false);

    companion object {
        val DEFAULT = NEWEST

        fun from(name: String?): OrderSort =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

fun List<Offer>.sortedBy(sort: OfferSort): List<Offer> {
    val within: Comparator<Offer> = when (sort) {
        OfferSort.PRICE_LOW -> compareBy { it.price }
        OfferSort.PRICE_HIGH -> compareByDescending { it.price }
        OfferSort.AVAILABILITY -> compareByDescending { it.available }
        // Operators that have not sold recently report no rate; rank them last
        // rather than treating a missing rate as a perfect one.
        OfferSort.SUCCESS_RATE -> compareByDescending { it.successRate ?: -1.0 }
    }
    return sortedWith(
        compareByDescending<Offer> { it.inStock }
            .then(within)
            .thenBy { it.operator },
    )
}

fun List<ServiceSummary>.sortedBy(sort: ServiceSort): List<ServiceSummary> {
    val within: Comparator<ServiceSummary> = when (sort) {
        ServiceSort.NAME -> compareBy { it.product.lowercase() }
        ServiceSort.PRICE_LOW -> compareBy { it.cheapestPrice }
        ServiceSort.PRICE_HIGH -> compareByDescending { it.cheapestPrice }
        ServiceSort.AVAILABILITY -> compareByDescending { it.available }
    }
    return sortedWith(
        compareByDescending<ServiceSummary> { it.available > 0 }
            .then(within)
            .thenBy { it.product.lowercase() },
    )
}
