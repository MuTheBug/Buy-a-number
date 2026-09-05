package com.buyanumber.app.data.repository

import com.buyanumber.app.data.remote.FiveSimApi
import com.buyanumber.app.data.remote.dto.PriceDto
import com.buyanumber.app.domain.model.Offer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Flattens the `guest/prices` tree into rows.
 *
 * The response nests country -> product -> operator, but 5sim drops levels
 * depending on which filters were sent, so the tree is walked until a leaf (an
 * object carrying `cost`/`count`) is reached and the path is read back from the
 * right — the last segment is always the operator. Levels the response omitted
 * fall back to the values that were queried.
 */
internal fun flattenPrices(
    root: JsonObject,
    fallbackCountry: String?,
    fallbackProduct: String?,
    json: Json,
): List<Offer> {
    val offers = mutableListOf<Offer>()

    fun walk(element: JsonElement, path: List<String>) {
        if (element !is JsonObject) return
        if (element.isPriceLeaf()) {
            val price = runCatching { json.decodeFromJsonElement(PriceDto.serializer(), element) }
                .getOrNull() ?: return
            offers += Offer(
                country = path.getOrNull(path.size - 3) ?: fallbackCountry.orEmpty(),
                product = path.getOrNull(path.size - 2) ?: fallbackProduct.orEmpty(),
                operator = path.lastOrNull() ?: FiveSimApi.ANY,
                price = price.cost,
                available = price.count,
                successRate = price.rate,
            )
            return
        }
        element.forEach { (key, child) -> walk(child, path + key) }
    }

    walk(root, emptyList())
    return offers
}

private fun JsonObject.isPriceLeaf(): Boolean =
    (containsKey("cost") || containsKey("count")) && values.all { it is JsonPrimitive }
