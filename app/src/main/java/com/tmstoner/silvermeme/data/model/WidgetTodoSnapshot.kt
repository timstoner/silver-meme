package com.tmstoner.silvermeme.data.model

import java.time.LocalDate
import java.util.Locale

/**
 * Widget-facing due-date buckets for incomplete tasks due today or earlier.
 *
 * [overdue] contains tasks whose due date is before the supplied "today" value.
 * [today] contains tasks whose due date is equal to the supplied "today" value.
 * [all] preserves the same ordering with overdue items first, then today's items.
 */
data class WidgetTodoSnapshot(
    val overdue: List<TodoItem> = emptyList(),
    val today: List<TodoItem> = emptyList()
) {
    val all: List<TodoItem>
        get() = overdue + today

    companion object {
        private val WIDGET_TODO_ORDER =
            compareBy<TodoItem> { it.dueDate }
                .thenByDescending { it.priority.sortOrder }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id.lowercase(Locale.ROOT) }

        fun fromTodos(
            todos: List<TodoItem>,
            today: LocalDate = LocalDate.now()
        ): WidgetTodoSnapshot {
            val dueTodos = todos.asSequence()
                .filter { !it.isCompleted }
                .mapNotNull { todo ->
                    todo.dueDate
                        ?.takeIf { dueDate -> dueDate.isBefore(today) || dueDate == today }
                        ?.let { todo }
                }
                .sortedWith(WIDGET_TODO_ORDER)
                .toList()

            return WidgetTodoSnapshot(
                overdue = dueTodos.filter { it.dueDate?.isBefore(today) == true },
                today = dueTodos.filter { it.dueDate == today }
            )
        }
    }
}
