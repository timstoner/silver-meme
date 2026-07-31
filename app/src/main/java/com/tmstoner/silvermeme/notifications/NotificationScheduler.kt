package com.tmstoner.silvermeme.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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

    fun schedule(todo: TodoItem) {
        val dueDate = todo.dueDate ?: return
        val triggerTime = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val now = Instant.now()
        if (triggerTime.isBefore(now)) return
        
        workManager.cancelAllWorkByTag(todo.id)
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
    }
}
