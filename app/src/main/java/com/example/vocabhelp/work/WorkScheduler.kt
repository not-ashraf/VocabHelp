package com.example.vocabhelp.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.vocabhelp.AppContainer
import java.util.Calendar
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
            .build()
        workManager.enqueueUniqueWork(
            PendingLookupWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun scheduleDailyReminder(hour: Int, minute: Int) {
        val initialDelay = computeInitialDelayMillis(hour, minute)
        val request = PeriodicWorkRequestBuilder<DueReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            DueReminderWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelDailyReminder() {
        workManager.cancelUniqueWork(DueReminderWorker.UNIQUE_NAME)
    }

    private fun computeInitialDelayMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }
}
