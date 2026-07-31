package com.tmstoner.silvermeme.data.repository

import com.tmstoner.silvermeme.data.model.TodoItem

interface TodoDataSource {
    suspend fun getTodos(): List<TodoItem>
    suspend fun saveTodo(todo: TodoItem, previousFilePath: String? = null): TodoItem
    suspend fun deleteTodo(todo: TodoItem)
    /** Moves [todo] to trash and returns the updated item with its new filePath. */
    suspend fun trashTodo(todo: TodoItem): TodoItem
    /** Moves a trashed [todo] back to Tasks/ and returns the restored item. */
    suspend fun restoreTodo(todo: TodoItem): TodoItem
    suspend fun getTrashedTodos(): List<TodoItem>
    /** Hard-deletes old trash entries; returns count of purged files. */
    suspend fun purgeOldTrash(): Int
    suspend fun pull(): GitRepository.GitResult
    suspend fun push(message: String = "Update todos"): GitRepository.GitResult
}
