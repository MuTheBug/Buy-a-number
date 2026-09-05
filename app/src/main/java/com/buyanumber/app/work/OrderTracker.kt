package com.buyanumber.app.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives background polling of open orders.
 *
 * WorkManager's periodic work has a 15-minute floor, which is far too slow for
 * an SMS code, so this chains short one-shot runs instead: each run reschedules
 * itself while orders are still open, and simply stops when none are.
 */
@Singleton
class OrderTracker @Inject constructor(
    private val workManager: WorkManager,
) {

    /** Called after a purchase, and by each poll that still has work to do. */
    fun scheduleNextPoll(delaySeconds: Long = POLL_INTERVAL_SECONDS) {
        val request = OneTimeWorkRequestBuilder<SmsPollWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        // REPLACE keeps exactly one poll queued no matter how many callers ask.
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun stop() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "sms-poll"
        const val POLL_INTERVAL_SECONDS = 30L
        const val BACKOFF_SECONDS = 30L
    }
}
