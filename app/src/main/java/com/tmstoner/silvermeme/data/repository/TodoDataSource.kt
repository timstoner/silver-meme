package com.tmstoner.silvermeme.data.repository

import com.tmstoner.silvermeme.data.model.TodoItem

interface TodoDataSource {
    suspend fun getTodos(): List<TodoItem>
    suspend fun saveTodo(todo: TodoItem): TodoItem
    suspend fun deleteTodo(todo: TodoItem)
    suspend fun pull(): GitRepository.GitResult
    suspend fun push(message: String = "Update todos"): GitRepository.GitResult
}
