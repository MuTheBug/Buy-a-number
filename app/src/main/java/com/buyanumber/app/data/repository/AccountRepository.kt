package com.buyanumber.app.data.repository

import com.buyanumber.app.core.apiCall
import com.buyanumber.app.data.local.ApiKeyStore
import com.buyanumber.app.data.local.db.TrackedOrderDao
import com.buyanumber.app.data.mapper.toDomain
import com.buyanumber.app.data.remote.FiveSimApi
import com.buyanumber.app.domain.model.OrderSort
import com.buyanumber.app.domain.model.OrdersPage
import com.buyanumber.app.domain.model.PaymentsPage
import com.buyanumber.app.domain.model.Profile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Account state: the key, the profile behind it, and the money history. */
@Singleton
class AccountRepository @Inject constructor(
    private val api: FiveSimApi,
    private val apiKeyStore: ApiKeyStore,
    private val trackedOrderDao: TrackedOrderDao,
) {

    val isSignedIn: Flow<Boolean> = apiKeyStore.isSignedIn

    /**
     * Checks a key against `user/profile` and stores it only if 5sim accepts
     * it, so the app never ends up signed in with a key that cannot work.
     */
    suspend fun signIn(apiKey: String): Result<Profile> =
        apiCall { api.profileWithKey(apiKey.trim()).toDomain() }
            .onSuccess { apiKeyStore.save(apiKey) }

    suspend fun signOut() {
        apiKeyStore.clear()
        // The cached orders belong to the account being signed out of.
        trackedOrderDao.clear()
    }

    suspend fun profile(): Result<Profile> = apiCall { api.profile().toDomain() }

    suspend fun orders(
        limit: Int,
        offset: Int,
        sort: OrderSort = OrderSort.DEFAULT,
        category: String = "activation",
    ): Result<OrdersPage> = apiCall {
        // History is paginated server-side, so the ordering has to be too —
        // sorting one loaded page locally would be a lie about the whole list.
        val page = api.orders(
            category = category,
            limit = limit,
            offset = offset,
            order = sort.apiField,
            reverse = sort.reverse,
        )
        OrdersPage(orders = page.data.map { it.toDomain() }, total = page.total)
    }

    suspend fun payments(limit: Int, offset: Int): Result<PaymentsPage> = apiCall {
        val page = api.payments(limit = limit, offset = offset)
        PaymentsPage(payments = page.data.map { it.toDomain() }, total = page.total)
    }
}
