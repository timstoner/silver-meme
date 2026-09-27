package com.tmstoner.silvermeme.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tmstoner.silvermeme.data.model.TodoItem
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class NotificationScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    init { ensureChannel() }

    /**
     * Schedules a WorkManager reminder for [todo] at its configured local time.
     * A null reminder time means midnight on the due date.
     * No-ops if: due date is null, due date is in the past, or POST_NOTIFICATIONS
     * permission is not granted (Android 13+).
     *
     * The actual runtime permission request is performed in TodoDetailScreen
     * (owned by the UI agent) when the user sets a due date. Call
     * [hasNotificationPermission] from the UI before scheduling to decide
     * whether to show the permission rationale dialog.
     */
    fun schedule(todo: TodoItem) {
        workManager.cancelAllWorkByTag(todo.id)
        if (!todo.reminderEnabled || todo.isCompleted || todo.dueDate == null) {
            return
        }
        val reminderTime = todo.reminderTime ?: LocalTime.MIDNIGHT
        val triggerTime = todo.dueDate.atTime(reminderTime)
            .atZone(ZoneId.systemDefault()).toInstant()
        val now = Instant.now()
        if (triggerTime.isBefore(now)) return
        // Only schedule if we have permission; the UI will request it separately.
        if (!hasNotificationPermission(appContext)) return

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, triggerTime))
            .setInputData(workDataOf("title" to todo.title, "todo_id" to todo.id))
            .addTag(todo.id)
            .build()
        workManager.enqueue(request)
    }

    fun cancel(todoId: String) {
        workManager.cancelAllWorkByTag(todoId)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Task Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        const val CHANNEL_ID = "todo_reminders"

        /**
         * Returns true if the app has permission to post notifications.
         * Always returns true on Android < 13 (TIRAMISU) where no runtime
         * permission is required. Expose as a static helper so the UI layer
         * (TodoDetailScreen) can check before launching the permission request.
         */
        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        }
    }
}
