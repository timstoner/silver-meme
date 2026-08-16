package com.tmstoner.silvermeme.domain.widget

import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.model.WidgetTodoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetTodoSnapshotTest {

    @Test
    fun `buildSnapshot keeps only incomplete overdue and today todos in widget order`() {
        val today = LocalDate.of(2026, 8, 16)
        val todos = listOf(
            todo(id = "future", title = "Future", dueDate = today.plusDays(1)),
            todo(id = "completed-overdue", title = "Done", dueDate = today.minusDays(3), isCompleted = true),
            todo(id = "today-medium", title = "B task", dueDate = today, priority = Priority.MEDIUM),
            todo(id = "today-urgent", title = "A task", dueDate = today, priority = Priority.URGENT),
            todo(id = "oldest-overdue", title = "Oldest", dueDate = today.minusDays(5), priority = Priority.LOW),
            todo(id = "recent-overdue", title = "Recent", dueDate = today.minusDays(1), priority = Priority.HIGH),
            todo(id = "no-date", title = "Someday", dueDate = null)
        )

        val snapshot = WidgetTodoSnapshot.fromTodos(todos, today)

        assertEquals(listOf("oldest-overdue", "recent-overdue"), snapshot.overdue.map { it.id })
        assertEquals(listOf("today-urgent", "today-medium"), snapshot.today.map { it.id })
        assertEquals(
            listOf("oldest-overdue", "recent-overdue", "today-urgent", "today-medium"),
            snapshot.all.map { it.id }
        )
    }

    private fun todo(
        id: String,
        title: String,
        dueDate: LocalDate?,
        priority: Priority = Priority.MEDIUM,
        isCompleted: Boolean = false
    ) = TodoItem(
        id = id,
        title = title,
        dueDate = dueDate,
        priority = priority,
        isCompleted = isCompleted,
        createdAt = LocalDateTime.of(2024, 1, 1, 0, 0),
        updatedAt = LocalDateTime.of(2024, 1, 1, 0, 0)
    )
}
