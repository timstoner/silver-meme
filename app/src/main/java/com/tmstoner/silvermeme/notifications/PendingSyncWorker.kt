package com.tmstoner.silvermeme.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tmstoner.silvermeme.SilverMemeApplication
import com.tmstoner.silvermeme.data.repository.GitRepository
import java.util.concurrent.TimeUnit

/**
 * Retries persisted local changes whenever WorkManager reports a connected
 * network. Failed attempts use WorkManager backoff and do not clear the durable
 * pending indicator.
 */
class PendingSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? SilverMemeApplication
            ?: return Result.failure()
        return when (application.todoRepository.retryPendingSync()) {
            GitRepository.GitResult.Success -> Result.success()
            is GitRepository.GitResult.Error,
            is GitRepository.GitResult.Conflict -> Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "silver_meme_pending_vault_sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PendingSyncWorker>()
                .setInitialDelay(10, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
