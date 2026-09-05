package com.buyanumber.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.buyanumber.app.data.local.ApiKeyStore
import com.buyanumber.app.data.local.SettingsStore
import com.buyanumber.app.data.local.db.TrackedOrderDao
import com.buyanumber.app.domain.model.NumberOrder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.buyanumber.app.data.repository.OrderRepository
import kotlinx.coroutines.flow.first

/**
 * Follows every open order in the background and raises a notification as soon
 * as an SMS arrives, so the user does not have to sit on the order screen.
 *
 * [OrderTracker] reschedules this worker for as long as orders remain open.
 */
@HiltWorker
class SmsPollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val orderRepository: OrderRepository,
    private val trackedOrderDao: TrackedOrderDao,
    private val apiKeyStore: ApiKeyStore,
    private val settingsStore: SettingsStore,
    private val notifier: SmsNotifier,
    private val tracker: OrderTracker,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (apiKeyStore.currentKey().isNullOrBlank()) return Result.success()

        val notify = settingsStore.settings.first().notifyOnSms
        val refreshed = orderRepository.refreshActive()

        if (notify) {
            refreshed.forEach { notifyIfNew(it) }
        } else {
            refreshed.forEach { order -> trackedOrderDao.markNotified(order.id, order.sms.size) }
        }

        // Keep following while anything is still live.
        if (refreshed.any { it.status.isActive }) {
            tracker.scheduleNextPoll()
        }
        return Result.success()
    }

    /**
     * Notifies only about messages received since the last run. The watermark
     * lives in the database so it survives the worker process being killed.
     */
    private suspend fun notifyIfNew(order: NumberOrder) {
        val alreadyNotified = trackedOrderDao.byId(order.id)?.notifiedSmsCount ?: 0
        if (order.sms.size > alreadyNotified) {
            notifier.notifySms(order)
            trackedOrderDao.markNotified(order.id, order.sms.size)
        }
    }
}
