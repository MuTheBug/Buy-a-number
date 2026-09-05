package com.buyanumber.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * 5sim reports several failures with HTTP 200 and a bare string body — most
 * importantly `no free phones` from the buy endpoint. Retrofit would hand that
 * to the JSON converter and raise a confusing parse error, so any successful
 * response whose body is not JSON is rewritten into a failure with the original
 * text intact for [com.buyanumber.app.core.FiveSimError.fromBody].
 */
class PlainTextErrorInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) return response

        val body = response.body ?: return response
        val peeked = response.peekBody(PEEK_BYTES).string().trim()
        // Empty bodies are legitimate for the order actions, and anything
        // starting a JSON document is left alone.
        if (peeked.isEmpty() || peeked.startsWith("{") || peeked.startsWith("[")) {
            return response
        }

        val contentType = body.contentType()
        response.close()
        return response.newBuilder()
            .code(PLAIN_TEXT_ERROR_CODE)
            .message(peeked.take(MESSAGE_LIMIT))
            .body(peeked.toResponseBody(contentType))
            .build()
    }

    private companion object {
        const val PEEK_BYTES = 4_096L
        const val MESSAGE_LIMIT = 120

        /** 409 Conflict: the request was understood but could not be fulfilled. */
        const val PLAIN_TEXT_ERROR_CODE = 409
    }
}
