package com.buyanumber.app.core

import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * Runs a 5sim call and normalises everything that can go wrong into
 * [FiveSimError], so callers never have to reason about HTTP or parsing.
 *
 * [CancellationException] is rethrown so structured concurrency keeps working —
 * a cancelled poll must not be reported to the user as a failure.
 */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Result.failure(throwable.toFiveSimError())
}

fun Throwable.toFiveSimError(): FiveSimError = when (this) {
    is FiveSimError -> this
    is HttpException -> mapHttpError()
    is IOException -> FiveSimError.Network
    else -> FiveSimError.Unknown(message.orEmpty())
}

private fun HttpException.mapHttpError(): FiveSimError {
    // errorBody() can only be consumed once, and reading it can itself fail if
    // the connection died mid-response.
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    FiveSimError.fromBody(body)?.let { return it }
    return when (code()) {
        401, 403 -> FiveSimError.Unauthorized
        404 -> FiveSimError.OrderNotFound
        429 -> FiveSimError.RateLimited
        in 500..599 -> FiveSimError.ServerError
        else -> FiveSimError.Unknown(body?.trim().orEmpty())
    }
}
