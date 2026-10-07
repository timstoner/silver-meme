package com.tmstoner.silvermeme.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tmstoner.silvermeme.SilverMemeApplication
import com.tmstoner.silvermeme.data.repository.GitRepository

/**
 * Periodic background sync (Track G1): pull from the remote, then push any local
 * commits, the same order the write path uses in `TodoRepository.syncIfConfigured()`.
 *
 * Only scheduled while the "Background sync" setting is on (see
 * [BackgroundSyncScheduler]). With no remote configured, `pull()`/`push()` return
 * success without doing anything, so the worker is harmless for local-only vaults.
 * Never throws: every outcome is mapped by [SyncOutcome.decide].
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SilverMemeApplication
        val repository = app.todoRepository

        val outcome = try {
            val pullResult = repository.pull()
            // Pushing after a failed or conflicting pull would be rejected (or overwrite
            // remote work), so only push once the pull has succeeded.
            val pushResult = if (pullResult is GitRepository.GitResult.Success) {
                repository.push("SilverMeme: background sync")
            } else null

            if (pullResult is GitRepository.GitResult.Success) {
                // Pulled files may add, move or complete tasks with due dates.
                runCatching { app.notificationScheduler.rescheduleAll(repository.getTodos()) }
            }
            SyncOutcome.decide(pullResult, pushResult, runAttemptCount)
        } catch (e: Exception) {
            Log.w(TAG, "background sync failed", e)
            SyncOutcome.decideOnException(runAttemptCount)
        }

        return when (outcome) {
            SyncOutcome.Action.SUCCESS -> Result.success()
            SyncOutcome.Action.RETRY   -> Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}
