package com.example.vocabhelp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.vocabhelp.appContainer

class PendingLookupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer()
        return try {
            // Words the dictionaries don't know stay pending; only network failures retry.
            val failed = container.wordRepository.retryAllPending()
            if (failed > 0) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "vocab_pending_lookup"
    }
}
