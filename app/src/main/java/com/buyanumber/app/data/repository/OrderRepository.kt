package com.buyanumber.app.data.repository

import com.buyanumber.app.core.FiveSimError
import com.buyanumber.app.core.apiCall
import com.buyanumber.app.core.toFiveSimError
import com.buyanumber.app.data.local.db.TrackedOrder
import com.buyanumber.app.data.local.db.TrackedOrderDao
import com.buyanumber.app.data.local.db.toEntity
import com.buyanumber.app.data.mapper.toDomain
import com.buyanumber.app.data.remote.FiveSimApi
import com.buyanumber.app.domain.model.NumberOrder
import com.buyanumber.app.domain.model.SmsMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Buying numbers, reading their SMS, and closing them out. */
@Singleton
class OrderRepository @Inject constructor(
    private val api: FiveSimApi,
    private val dao: TrackedOrderDao,
) {

    /** Actions the user can take on an open order. */
    enum class Action { FINISH, CANCEL, BAN }

    val trackedOrders: Flow<List<NumberOrder>> =
        dao.observeAll().map { rows -> rows.map(TrackedOrder::toDomain) }

    val activeOrders: Flow<List<NumberOrder>> =
        dao.observeActive().map { rows -> rows.map(TrackedOrder::toDomain) }

    suspend fun buy(
        country: String,
        operator: String,
        product: String,
        maxPrice: Double? = null,
    ): Result<NumberOrder> = apiCall {
        api.buyActivation(
            country = country,
            operator = operator,
            product = product,
            maxPrice = maxPrice,
        ).toDomain()
    }.onSuccess { cache(it) }

    /** Polls one order and mirrors the result locally. */
    suspend fun check(id: Long): Result<NumberOrder> =
        apiCall { api.check(id).toDomain() }.onSuccess { cache(it) }

    /**
     * Applies a terminal action and returns the resulting order.
     *
     * The action endpoints echo the updated order, but 5sim has been known to
     * answer with an empty body, so the state is re-read with `check` rather
     * than parsed from the action response.
     */
    suspend fun applyAction(id: Long, action: Action): Result<NumberOrder> = try {
        when (action) {
            Action.FINISH -> api.finish(id)
            Action.CANCEL -> api.cancel(id)
            Action.BAN -> api.ban(id)
        }.close()
        check(id)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable.toFiveSimError())
    }

    /**
     * Re-reads every order still open, so the dashboard and the background
     * poller share one code path. Individual failures are swallowed: one dead
     * order must not stop the rest from refreshing.
     */
    suspend fun refreshActive(): List<NumberOrder> {
        val refreshed = mutableListOf<NumberOrder>()
        for (row in dao.activeOrders()) {
            val result = check(row.id)
            result.getOrNull()?.let { refreshed += it }
            // An order 5sim has forgotten is no longer worth polling.
            if (result.exceptionOrNull() is FiveSimError.OrderNotFound) {
                dao.delete(row.id)
            }
        }
        return refreshed
    }

    /**
     * Seeds the local mirror from the account's server-side history, so a fresh
     * install or a number bought on another device still shows up.
     */
    suspend fun syncFromHistory(limit: Int = 50): Result<List<NumberOrder>> = apiCall {
        val page = api.orders(category = "activation", limit = limit, offset = 0)
        val orders = page.data.map { it.toDomain() }
        val existing = dao.activeOrders().associateBy { it.id }
        dao.upsertAll(
            orders.map { order ->
                // Preserve the notification watermark so re-syncing does not
                // replay alerts for messages the user has already seen.
                order.toEntity(notifiedSmsCount = existing[order.id]?.notifiedSmsCount ?: order.sms.size)
            },
        )
        orders
    }

    suspend fun smsInbox(id: Long): Result<List<SmsMessage>> =
        apiCall { api.smsInbox(id).data.map { it.toDomain() } }

    suspend fun forget(id: Long) = dao.delete(id)

    private suspend fun cache(order: NumberOrder) {
        val previous = dao.byId(order.id)
        dao.upsert(order.toEntity(notifiedSmsCount = previous?.notifiedSmsCount ?: 0))
    }
}
