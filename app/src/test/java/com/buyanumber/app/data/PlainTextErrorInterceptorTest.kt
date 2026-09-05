package com.buyanumber.app.data

import com.buyanumber.app.core.FiveSimError
import com.buyanumber.app.core.apiCall
import com.buyanumber.app.data.remote.PlainTextErrorInterceptor
import com.buyanumber.app.data.remote.dto.OrderDto
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.http.GET

/**
 * 5sim signals some failures with HTTP 200 and a bare string body. These tests
 * pin down that the interceptor turns those into typed errors instead of
 * letting the JSON converter blow up.
 */
class PlainTextErrorInterceptorTest {

    private interface TestApi {
        @GET("buy")
        suspend fun buy(): OrderDto
    }

    private lateinit var server: MockWebServer
    private lateinit var api: TestApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(PlainTextErrorInterceptor()).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TestApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `turns a 200 with no free phones into a typed error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("no free phones"))

        val result = apiCall { api.buy() }

        assertTrue(result.isFailure)
        assertEquals(FiveSimError.NoFreePhones, result.exceptionOrNull())
    }

    @Test
    fun `turns a 200 with a low balance message into a typed error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not enough user balance"))

        assertEquals(FiveSimError.InsufficientBalance, apiCall { api.buy() }.exceptionOrNull())
    }

    @Test
    fun `leaves a real JSON body alone`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id": 7, "phone": "+79001112233", "product": "telegram", "price": 3, "status": "PENDING"}""",
            ),
        )

        val result = apiCall { api.buy() }

        assertTrue(result.isSuccess)
        assertEquals(7L, result.getOrThrow().id)
    }

    @Test
    fun `maps a 401 to an authorization failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized - Invalid API Key"))

        assertEquals(FiveSimError.Unauthorized, apiCall { api.buy() }.exceptionOrNull())
    }

    @Test
    fun `maps a 429 to a rate limit`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        assertEquals(FiveSimError.RateLimited, apiCall { api.buy() }.exceptionOrNull())
    }

    @Test
    fun `maps a server failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(FiveSimError.ServerError, apiCall { api.buy() }.exceptionOrNull())
    }
}
