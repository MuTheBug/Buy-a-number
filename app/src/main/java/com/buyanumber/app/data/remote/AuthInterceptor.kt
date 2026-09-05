package com.buyanumber.app.data.remote

import com.buyanumber.app.data.local.ApiKeyStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the account's API key to `user/` calls.
 *
 * `guest/` endpoints are public, so the catalog stays browsable before a key
 * has been entered and an expired key never blocks price lookups.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.contains("/guest/")) {
            return chain.proceed(request)
        }
        // A per-call override lets the sign-in screen verify a key before it
        // is stored. Otherwise use the saved key; OkHttp interceptors are
        // blocking by contract, and the store keeps it decrypted in memory
        // after the first read.
        val override = request.header(FiveSimApi.KEY_OVERRIDE_HEADER)
        val key = override ?: runBlocking { apiKeyStore.currentKey() }

        val builder = request.newBuilder().removeHeader(FiveSimApi.KEY_OVERRIDE_HEADER)
        if (!key.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $key")
        }
        builder.header("Accept", "application/json")
        return chain.proceed(builder.build())
    }
}
