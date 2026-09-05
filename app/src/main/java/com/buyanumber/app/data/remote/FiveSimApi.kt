package com.buyanumber.app.data.remote

import com.buyanumber.app.data.remote.dto.CountryDto
import com.buyanumber.app.data.remote.dto.OrderDto
import com.buyanumber.app.data.remote.dto.OrdersHistoryDto
import com.buyanumber.app.data.remote.dto.PaymentsHistoryDto
import com.buyanumber.app.data.remote.dto.ProductDto
import com.buyanumber.app.data.remote.dto.ProfileDto
import com.buyanumber.app.data.remote.dto.SmsInboxDto
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Typed bindings for the endpoints documented at https://5sim.net/docs.
 *
 * Everything under `user/` is authenticated with the account's API key;
 * [AuthInterceptor] attaches it. Everything under `guest/` is public, so the
 * catalog can be browsed before a key is entered.
 */
interface FiveSimApi {

    companion object {
        const val BASE_URL = "https://5sim.net/v1/"

        /** Passed as the operator segment to let 5sim pick the cheapest one. */
        const val ANY = "any"

        /**
         * Lets a single call authenticate with a key that has not been saved
         * yet, so a key can be verified before the app signs in with it.
         * [AuthInterceptor] consumes and strips this header.
         */
        const val KEY_OVERRIDE_HEADER = "X-Api-Key-Override"
    }

    // ---------------------------------------------------------------- account

    @GET("user/profile")
    suspend fun profile(): ProfileDto

    /** Verifies a key the user just typed, without persisting it first. */
    @GET("user/profile")
    suspend fun profileWithKey(
        @Header(KEY_OVERRIDE_HEADER) apiKey: String,
    ): ProfileDto

    @GET("user/orders")
    suspend fun orders(
        @Query("category") category: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("order") order: String = "id",
        @Query("reverse") reverse: Boolean = true,
    ): OrdersHistoryDto

    @GET("user/payments")
    suspend fun payments(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("order") order: String = "ID",
        @Query("reverse") reverse: Boolean = true,
    ): PaymentsHistoryDto

    // ----------------------------------------------------------------- buying

    /**
     * Buys one activation number. 5sim answers `no free phones` in plain text
     * when the operator is sold out; [PlainTextErrorInterceptor] turns that
     * into a failure so it surfaces as [FiveSimError.NoFreePhones].
     */
    @GET("user/buy/activation/{country}/{operator}/{product}")
    suspend fun buyActivation(
        @Path("country") country: String,
        @Path("operator") operator: String,
        @Path("product") product: String,
        @Query("forwarding") forwarding: Boolean? = null,
        @Query("number") forwardingNumber: String? = null,
        @Query("reuse") reuse: Int? = null,
        @Query("voice") voice: Int? = null,
        @Query("maxPrice") maxPrice: Double? = null,
    ): OrderDto

    /** Rents a number for a fixed window (`3hours`, `1day`, `10days`, `1month`). */
    @GET("user/buy/hosting/{country}/{operator}/{product}")
    suspend fun buyHosting(
        @Path("country") country: String,
        @Path("operator") operator: String,
        @Path("product") product: String,
    ): OrderDto

    /** Buys the same number again for the same product, when 5sim still holds it. */
    @GET("user/reuse/{product}/{number}")
    suspend fun reuse(
        @Path("product") product: String,
        @Path("number") number: String,
    ): ResponseBody

    // ----------------------------------------------------------------- orders

    /** Returns the order with every SMS received so far. This is the poll call. */
    @GET("user/check/{id}")
    suspend fun check(@Path("id") id: Long): OrderDto

    /** Closes an order once the code has been used. */
    @GET("user/finish/{id}")
    suspend fun finish(@Path("id") id: Long): ResponseBody

    /** Refunds an order that never received an SMS. */
    @GET("user/cancel/{id}")
    suspend fun cancel(@Path("id") id: Long): ResponseBody

    /** Reports the number as unusable so 5sim stops selling it. */
    @GET("user/ban/{id}")
    suspend fun ban(@Path("id") id: Long): ResponseBody

    @GET("user/sms/inbox/{id}")
    suspend fun smsInbox(@Path("id") id: Long): SmsInboxDto

    // ---------------------------------------------------------------- catalog

    @GET("guest/countries")
    suspend fun countries(): Map<String, CountryDto>

    @GET("guest/products/{country}/{operator}")
    suspend fun products(
        @Path("country") country: String,
        @Path("operator") operator: String,
    ): Map<String, ProductDto>

    /**
     * Prices, availability and success rate. The response is a
     * country -> product -> operator tree; the shape shifts with the filters
     * used, so it is decoded as raw JSON and flattened by
     * [com.buyanumber.app.data.repository.CatalogRepository].
     */
    @GET("guest/prices")
    suspend fun prices(
        @Query("country") country: String? = null,
        @Query("product") product: String? = null,
    ): JsonObject
}
