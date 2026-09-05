package com.buyanumber.app.core

/**
 * Every failure the app can show the user, translated from 5sim's plain-text
 * error bodies and HTTP status codes.
 *
 * The API answers with a bare string such as `no free phones` rather than a
 * JSON error object, so the raw text is matched against the documented set and
 * anything unrecognised falls through to [Unknown] with the text preserved.
 */
sealed class FiveSimError(val userMessage: String) : Exception(userMessage) {

    /** The saved API key was rejected — the user has to enter a new one. */
    data object Unauthorized : FiveSimError(
        "Your API key was rejected. Check that it is still valid in your 5sim profile.",
    )

    data object RateLimited : FiveSimError(
        "Too many requests. Give 5sim a moment and try again.",
    )

    data object NoFreePhones : FiveSimError(
        "No numbers left for this operator right now. Try another operator or country.",
    )

    data object InsufficientBalance : FiveSimError(
        "Your 5sim balance is too low for this purchase.",
    )

    data object InsufficientRating : FiveSimError(
        "Your 5sim rating is too low to buy this number.",
    )

    data object OrderNotFound : FiveSimError(
        "That order no longer exists — it may have already expired.",
    )

    data object OrderHasSms : FiveSimError(
        "This order already received an SMS, so it can no longer be cancelled.",
    )

    data object OrderNoSms : FiveSimError(
        "No SMS has arrived yet, so this order cannot be finished.",
    )

    data object CancelTooEarly : FiveSimError(
        "A number can only be cancelled two minutes after it is bought.",
    )

    data object BadSelection : FiveSimError(
        "5sim does not offer that country, operator and service combination.",
    )

    data object ServerError : FiveSimError(
        "5sim is having trouble right now. Try again in a moment.",
    )

    data object Network : FiveSimError(
        "No connection to 5sim. Check your internet and try again.",
    )

    class Unknown(detail: String) : FiveSimError(
        detail.ifBlank { "Something went wrong talking to 5sim." },
    )

    companion object {
        /** Maps a plain-text error body onto a typed error. */
        fun fromBody(rawBody: String?): FiveSimError? {
            val body = rawBody?.trim()?.lowercase().orEmpty()
            if (body.isEmpty()) return null
            return when {
                body.contains("no free phones") -> NoFreePhones
                body.contains("not enough user balance") -> InsufficientBalance
                body.contains("not enough rating") -> InsufficientRating
                body.contains("order not found") -> OrderNotFound
                body.contains("order has sms") -> OrderHasSms
                body.contains("order no sms") -> OrderNoSms
                body.contains("you need to wait time") -> CancelTooEarly
                body.contains("record not found") -> OrderNotFound
                body.contains("invalid api key") || body.contains("unauthorized") -> Unauthorized
                body.contains("country is incorrect") ||
                    body.contains("product is incorrect") ||
                    body.contains("bad country") ||
                    body.contains("bad operator") ||
                    body.contains("select country") ||
                    body.contains("select operator") ||
                    body.contains("no product") -> BadSelection
                body.contains("internal error") || body.contains("server offline") -> ServerError
                else -> null
            }
        }
    }
}
