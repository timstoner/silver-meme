package com.tmstoner.silvermeme.data.repository

import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.model.WidgetTodoSnapshot
import java.time.LocalDate

interface TodoDataSource {
    suspend fun getTodos(): List<TodoItem>
    /**
     * Returns incomplete tasks due today or earlier, split into overdue and today buckets.
     * Results are sorted by due date ascending, then priority descending, then title/id.
     */
    suspend fun getWidgetTodoSnapshot(today: LocalDate = LocalDate.now()): WidgetTodoSnapshot
    suspend fun saveTodo(todo: TodoItem, previousFilePath: String? = null): TodoItem
    suspend fun deleteTodo(todo: TodoItem)
    /** Moves [todo] to trash and returns the updated item with its new filePath. */
    suspend fun trashTodo(todo: TodoItem): TodoItem
    /** Moves a trashed [todo] back to Tasks/ and returns the restored item. */
    suspend fun restoreTodo(todo: TodoItem): TodoItem
    suspend fun getTrashedTodos(): List<TodoItem>
    /** Hard-deletes old trash entries; returns count of purged files. */
    suspend fun purgeOldTrash(): Int
    /** Saves multiple todos in a single batch and syncs once at the end. */
    suspend fun bulkSave(todos: List<TodoItem>)
    /** Moves multiple todos to trash in a single batch and syncs once at the end. */
    suspend fun bulkTrash(todos: List<TodoItem>)
    suspend fun pull(): GitRepository.GitResult
    suspend fun push(message: String = "Update todos"): GitRepository.GitResult
}
