package com.example.vocabhelp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.vocabhelp.appContainer
import com.example.vocabhelp.notifications.DueWordsNotifier

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
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "vocab_due_reminder"
    }
}
