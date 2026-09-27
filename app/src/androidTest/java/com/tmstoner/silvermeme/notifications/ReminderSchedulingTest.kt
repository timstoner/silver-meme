package com.tmstoner.silvermeme.notifications

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tmstoner.silvermeme.data.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReminderSchedulingTest {
    @Test
    fun futureReminderIsEnqueuedAndDisabledOrCompletedReminderIsCancelled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val workManager = WorkManager.getInstance(context)
        val scheduler = NotificationScheduler(context)
        val tag = "reminder-test-${UUID.randomUUID()}"
        val todo = TodoItem(
            id = tag,
            title = "Scheduled task",
            dueDate = LocalDate.now().plusDays(30),
            reminderEnabled = true,
            reminderTime = LocalTime.of(8, 45)
        )

        try {
            scheduler.schedule(todo)

            val scheduled = workManager.getWorkInfosByTag(tag).get(10, TimeUnit.SECONDS)
            if (NotificationScheduler.hasNotificationPermission(context)) {
                assertEquals(1, scheduled.size)
                assertEquals(WorkInfo.State.ENQUEUED, scheduled.single().state)
            } else {
                assertTrue("Permission denial must prevent scheduling", scheduled.isEmpty())
            }

            enqueueDelayedTaggedWork(workManager, tag)
            scheduler.schedule(todo.copy(reminderEnabled = false))
            assertAllTaggedWorkCancelled(workManager, tag)

            enqueueDelayedTaggedWork(workManager, tag)
            scheduler.schedule(todo.copy(isCompleted = true))
            assertAllTaggedWorkCancelled(workManager, tag)

            enqueueDelayedTaggedWork(workManager, tag)
            scheduler.schedule(todo.copy(dueDate = null))
            assertAllTaggedWorkCancelled(workManager, tag)

            enqueueDelayedTaggedWork(workManager, tag)
            scheduler.schedule(todo.copy(dueDate = LocalDate.now().minusDays(1)))
            assertAllTaggedWorkCancelled(workManager, tag)
        } finally {
            scheduler.cancel(tag)
        }
    }

    private fun enqueueDelayedTaggedWork(workManager: WorkManager, tag: String) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(30, TimeUnit.DAYS)
            .addTag(tag)
            .build()
        workManager.enqueue(request).result.get(10, TimeUnit.SECONDS)
        assertTrue(
            workManager.getWorkInfosByTag(tag).get(10, TimeUnit.SECONDS)
                .any { it.state == WorkInfo.State.ENQUEUED }
        )
    }

    private fun assertAllTaggedWorkCancelled(workManager: WorkManager, tag: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var tagged = emptyList<WorkInfo>()
        while (System.nanoTime() < deadline) {
            tagged = workManager.getWorkInfosByTag(tag).get(10, TimeUnit.SECONDS)
            if (tagged.isNotEmpty() && tagged.all { it.state == WorkInfo.State.CANCELLED }) return
            Thread.sleep(25)
        }
        assertTrue("Expected all tagged work to be cancelled: $tagged", tagged.isNotEmpty())
        assertTrue(tagged.all { it.state == WorkInfo.State.CANCELLED })
    }
}
