package com.tmstoner.silvermeme.viewmodel

import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.repository.GitRepository
import com.tmstoner.silvermeme.data.repository.TodoDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `filteredTodos applies search priority overdue and sort`() = runTest {
        val overdue = sampleTodo(
            id = "1",
            title = "Finish report",
            dueDate = LocalDate.now().minusDays(1),
            priority = Priority.HIGH,
            tags = listOf("work")
        )
        val future = sampleTodo(
            id = "2",
            title = "Book trip",
            dueDate = LocalDate.now().plusDays(2),
            priority = Priority.LOW,
            tags = listOf("travel")
        )
        val repository = FakeTodoDataSource(mutableListOf(overdue, future))
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()

        viewModel.setSearchQuery("report")
        viewModel.setFilterPriority(Priority.HIGH)
        viewModel.setFilterOverdue(true)
        viewModel.setSortOrder(SortOrder.TITLE_ASC)

        val filtered = viewModel.filteredTodos()
        assertEquals(listOf(overdue), filtered)
    }

    @Test
    fun `toggleComplete saves updated todo`() = runTest {
        val todo = sampleTodo(id = "toggle", isCompleted = false)
        val repository = FakeTodoDataSource(mutableListOf(todo))
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()

        viewModel.toggleComplete(todo)
        advanceUntilIdle()

        assertTrue(repository.saved.any { it.id == "toggle" && it.isCompleted })
    }

    @Test
    fun `toggleComplete on recurring todo spawns next occurrence with advanced due date`() = runTest {
        val today = LocalDate.now()
        val todo = sampleTodo(
            id = "recurring",
            title = "Water plants",
            dueDate = today,
            isCompleted = false,
            recurrence = "daily"
        )
        val repository = FakeTodoDataSource(mutableListOf(todo))
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()

        viewModel.toggleComplete(todo)
        advanceUntilIdle()

        // The original occurrence is saved as completed.
        val originalSave = repository.saved.first { it.id == todo.id }
        assertTrue(originalSave.isCompleted)

        // A new, incomplete occurrence is spawned with a distinct id and advanced due date.
        val spawned = repository.saved.singleOrNull { it.id != todo.id }
        assertTrue(spawned != null)
        assertEquals(false, spawned!!.isCompleted)
        assertEquals(today.plusDays(1), spawned.dueDate)
        assertTrue(spawned.filePath.isBlank())
    }

    @Test
    fun `toggleComplete on non-recurring todo does not spawn a next occurrence`() = runTest {
        val todo = sampleTodo(id = "one-off", isCompleted = false, recurrence = "none")
        val repository = FakeTodoDataSource(mutableListOf(todo))
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()

        viewModel.toggleComplete(todo)
        advanceUntilIdle()

        assertEquals(1, repository.saved.size)
        assertTrue(repository.saved.single().isCompleted)
    }

    @Test
    fun `syncFromRemote exposes success state`() = runTest {
        val repository = FakeTodoDataSource(mutableListOf(), pullResult = GitRepository.GitResult.Success)
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()

        viewModel.syncFromRemote()
        advanceUntilIdle()

        assertTrue(viewModel.syncState.value is SyncState.Success)
    }

    private fun sampleTodo(
        id: String,
        title: String = "Task",
        dueDate: LocalDate? = null,
        priority: Priority = Priority.MEDIUM,
        tags: List<String> = emptyList(),
        isCompleted: Boolean = false,
        recurrence: String = "none"
    ) = TodoItem(
        id = id,
        title = title,
        content = "notes",
        dueDate = dueDate,
        priority = priority,
        tags = tags,
        isCompleted = isCompleted,
        recurrence = recurrence,
        createdAt = LocalDateTime.of(2024, 1, 1, 0, 0)
    )
}

private class FakeTodoDataSource(
    private val todos: MutableList<TodoItem>,
    private val pullResult: GitRepository.GitResult = GitRepository.GitResult.Success,
    private val pushResult: GitRepository.GitResult = GitRepository.GitResult.Success
) : TodoDataSource {
    val saved = mutableListOf<TodoItem>()

    override suspend fun getTodos(): List<TodoItem> = todos.toList()

    override suspend fun saveTodo(todo: TodoItem, previousFilePath: String?): TodoItem {
        saved += todo
        todos.removeAll { it.id == todo.id }
        todos += todo
        return todo
    }

    override suspend fun deleteTodo(todo: TodoItem) {
        todos.removeAll { it.id == todo.id }
    }

    override suspend fun trashTodo(todo: TodoItem): TodoItem {
        todos.removeAll { it.id == todo.id }
        val trashed = todo.copy(filePath = "Tasks/.trash/${todo.id}.md")
        trashed
        return trashed
    }

    override suspend fun restoreTodo(todo: TodoItem): TodoItem {
        val restored = todo.copy(filePath = "Tasks/${todo.id}.md")
        todos += restored
        return restored
    }

    override suspend fun getTrashedTodos(): List<TodoItem> = emptyList()

    override suspend fun purgeOldTrash(): Int = 0

    override suspend fun bulkSave(todos: List<TodoItem>) {
        todos.forEach { saveTodo(it) }
    }

    override suspend fun bulkTrash(todos: List<TodoItem>) {
        todos.forEach { trashTodo(it) }
    }

    override suspend fun pull(): GitRepository.GitResult = pullResult

    override suspend fun push(message: String): GitRepository.GitResult = pushResult
}
