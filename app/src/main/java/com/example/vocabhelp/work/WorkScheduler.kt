package com.example.vocabhelp.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.vocabhelp.AppContainer
import com.example.vocabhelp.sm2.millisUntilNext
import java.util.concurrent.TimeUnit

/**
 * Wraps WorkManager scheduling so callers don't need to reach for the framework.
 * Constructed with the AppContainer so it can grab the application Context.
 */
class WorkScheduler(
    private val context: Context,
) {
    constructor(container: AppContainer) : this(container.context)

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun enqueuePendingLookup() {
        val request = OneTimeWorkRequestBuilder<PendingLookupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(
            PendingLookupWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Schedules the next reminder as one-shot work at the next [hour]:[minute];
     * [DueReminderWorker] schedules the following day's when it runs. (A 24h
     * PeriodicWorkRequest stays anchored to the time of its first run, so
     * changing the reminder time never took effect.)
     *
     * [replaceExisting] = true when the time/enabled setting changed; false on
     * app start, so launching the app doesn't disturb a pending reminder.
     */
    fun scheduleDailyReminder(hour: Int, minute: Int, replaceExisting: Boolean = true) {
        workManager.cancelUniqueWork(DueReminderWorker.LEGACY_PERIODIC_NAME)
        val request = OneTimeWorkRequestBuilder<DueReminderWorker>()
            .setInitialDelay(millisUntilNext(hour, minute, System.currentTimeMillis()), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(
            DueReminderWorker.UNIQUE_NAME,
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelDailyReminder() {
        workManager.cancelUniqueWork(DueReminderWorker.UNIQUE_NAME)
        workManager.cancelUniqueWork(DueReminderWorker.LEGACY_PERIODIC_NAME)
    }
}
