package com.tmstoner.silvermeme.domain.widget

import com.tmstoner.silvermeme.data.model.WidgetTodoSnapshot
import com.tmstoner.silvermeme.data.repository.TodoDataSource
import java.time.LocalDate

/**
 * Loads incomplete overdue and due-today tasks for home-screen widgets.
 *
 * The returned lists are sorted by due date ascending, then priority descending,
 * then title/id for stable display ordering.
 */
class LoadWidgetTodosUseCase(
    private val todoRepository: TodoDataSource
) {
    suspend operator fun invoke(today: LocalDate = LocalDate.now()): WidgetTodoSnapshot =
        todoRepository.getWidgetTodoSnapshot(today)
}
