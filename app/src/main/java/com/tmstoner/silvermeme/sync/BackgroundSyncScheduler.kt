package com.tmstoner.silvermeme.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Turns the periodic [SyncWorker] on or off to match the "Background sync" setting.
 *
 * WorkManager persists periodic work across reboots and app updates itself; [apply]
 * is also called on every app start (from `SilverMemeApplication`) so the schedule
 * always matches the saved setting.
 */
class BackgroundSyncScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun apply(enabled: Boolean) {
        if (enabled) enable() else disable()
    }

    private fun enable() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        // KEEP: an already-scheduled sync keeps its timer instead of restarting on each launch.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun disable() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "background_sync"
        const val INTERVAL_HOURS = 1L
    }
}
