package com.example.vocabhelp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.vocabhelp.appContainer
import com.example.vocabhelp.notifications.DueWordsNotifier
import kotlinx.coroutines.flow.first

class DueReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer()
        val due = container.wordDao.getDueOnce(System.currentTimeMillis())
        if (due.isNotEmpty()) {
            DueWordsNotifier(applicationContext).notify(due.size)
        }
        // Chain tomorrow's reminder. Done last: REPLACE cancels this (already
        // finished) run, which is harmless.
        val settings = container.settingsRepository.settings.first()
        if (settings.enabled) {
            container.workScheduler.scheduleDailyReminder(settings.hour, settings.minute)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "vocab_due_reminder_daily"

        /** The old 24h periodic work; cancelled whenever a reminder is scheduled. */
        const val LEGACY_PERIODIC_NAME = "vocab_due_reminder"
    }
}
