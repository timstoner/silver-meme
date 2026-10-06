package com.tmstoner.silvermeme.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tmstoner.silvermeme.SilverMemeApplication
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Re-schedules all pending (non-completed) task reminders after a device reboot.
 * WorkManager one-time work with a delay is not guaranteed to survive reboot, so
 * this receiver re-enqueues them on BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as SilverMemeApplication
        val scheduler = app.notificationScheduler
        // Launch a coroutine to reload todos and reschedule
        GlobalScope.launch(Dispatchers.IO) {
            try {
                scheduler.rescheduleAll(app.todoRepository.getTodos())
            } catch (e: Exception) {
                // Notifications are best-effort; ignore errors during reboot restore
            }
        }
    }
}
