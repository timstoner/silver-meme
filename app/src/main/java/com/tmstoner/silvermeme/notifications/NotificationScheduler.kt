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
import java.time.ZoneId

class NotificationScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    init { ensureChannel() }

    /**
     * Schedules a WorkManager reminder for [todo] at start-of-day on its due date.
     * No-ops if: due date is null, due date is in the past, or POST_NOTIFICATIONS
     * permission is not granted (Android 13+).
     *
     * The runtime permission request is made by MainActivity on launch; once
     * granted it calls [rescheduleAll] so tasks saved before the grant get reminders.
     */
    fun schedule(todo: TodoItem) {
        val dueDate = todo.dueDate ?: return
        val triggerTime = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val now = Instant.now()
        if (triggerTime.isBefore(now)) return
        // Only schedule if we have permission; the UI will request it separately.
        if (!hasNotificationPermission(appContext)) return

        workManager.cancelAllWorkByTag(todo.id)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, triggerTime))
            .setInputData(workDataOf("title" to todo.title, "todo_id" to todo.id))
            .addTag(todo.id)
            .build()
        workManager.enqueue(request)
    }

    /** Schedules reminders for every open task with a due date (boot restore, permission grant). */
    fun rescheduleAll(todos: List<TodoItem>) {
        todos.filter { !it.isCompleted && it.dueDate != null }.forEach { schedule(it) }
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
         * permission is required.
         */
        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        }
    }
}
