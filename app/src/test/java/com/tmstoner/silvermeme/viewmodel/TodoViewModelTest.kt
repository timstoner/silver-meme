package com.tmstoner.silvermeme.viewmodel

import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.model.WidgetTodoSnapshot
import com.tmstoner.silvermeme.data.repository.GitRepository
import com.tmstoner.silvermeme.data.repository.TodoDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import java.time.LocalTime

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
    fun `save updates the current task list without rescanning the vault`() = runTest {
        val repository = FakeTodoDataSource(mutableListOf(sampleTodo(id = "existing")))
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()
        val initialReadCount = repository.getTodosCalls

        viewModel.saveTodo(sampleTodo(id = "new"))
        advanceUntilIdle()

        assertEquals(initialReadCount, repository.getTodosCalls)
        assertTrue(viewModel.uiState.value.todos.any { it.id == "new" })
    }

    @Test
    fun `save retains reminder opt out and selected local time`() = runTest {
        val repository = FakeTodoDataSource(mutableListOf())
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()
        val reminderTime = LocalTime.of(8, 45)

        viewModel.saveTodo(
            sampleTodo(id = "reminder").copy(
                reminderEnabled = false,
                reminderTime = reminderTime
            )
        )
        advanceUntilIdle()

        assertEquals(false, repository.saved.single().reminderEnabled)
        assertEquals(reminderTime, repository.saved.single().reminderTime)
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

    @Test
    fun `failed manual pull retry repeats the pull even when no write is pending`() = runTest {
        val repository = FakeTodoDataSource(
            mutableListOf(),
            pullResult = GitRepository.GitResult.Error("Offline")
        )
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()

        viewModel.syncFromRemote()
        advanceUntilIdle()
        viewModel.retryFailedSync()
        advanceUntilIdle()

        assertEquals(2, repository.pullCalls)
        assertEquals(SyncState.Failure("Offline"), viewModel.syncState.value)
    }

    @Test
    fun `automatic sync error is exposed to the UI`() = runTest {
        val repository = FakeTodoDataSource(mutableListOf())
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()

        repository.automaticSyncEvents.emit(GitRepository.GitResult.Error("Network unavailable"))
        advanceUntilIdle()

        assertEquals(SyncState.Failure("Network unavailable"), viewModel.syncState.value)
    }

    @Test
    fun `automatic sync success clears a previous error`() = runTest {
        val repository = FakeTodoDataSource(mutableListOf())
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()

        repository.automaticSyncEvents.emit(GitRepository.GitResult.Error("Offline"))
        advanceUntilIdle()
        assertEquals(SyncState.Failure("Offline"), viewModel.syncState.value)

        repository.automaticSyncEvents.emit(GitRepository.GitResult.Success)
        advanceUntilIdle()
        assertEquals(SyncState.Idle, viewModel.syncState.value)
    }

    @Test
    fun `retry pending sync exposes retry errors`() = runTest {
        val repository = FakeTodoDataSource(
            mutableListOf(),
            retryResult = GitRepository.GitResult.Error("Offline")
        )
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()

        viewModel.retryPendingSync()
        advanceUntilIdle()

        assertEquals(SyncState.Failure("Offline"), viewModel.syncState.value)
    }

    @Test
    fun `bulk trash can be undone`() = runTest {
        val todo = sampleTodo(id = "trash-me", filePath = "Tasks/Work/Task.md")
        val repository = FakeTodoDataSource(mutableListOf(todo))
        val viewModel = TodoViewModel(repository)
        advanceUntilIdle()
        viewModel.toggleSelection(todo.id)
        var trashedCount = 0

        viewModel.bulkTrash { trashedCount = it }
        advanceUntilIdle()
        assertEquals(1, trashedCount)
        assertTrue(viewModel.uiState.value.todos.none { it.id == todo.id })

        viewModel.undoLastTrash()
        advanceUntilIdle()

        assertEquals("Tasks/Work/Task.md", viewModel.uiState.value.todos.single().filePath)
        assertTrue(viewModel.trashedTodos.value.isEmpty())
    }

    @Test
    fun `dashboard summary counts unfiltered task states`() {
        val today = LocalDate.of(2026, 9, 27)
        val summary = DashboardSummary.fromTodos(
            todos = listOf(
                sampleTodo(id = "overdue", dueDate = today.minusDays(1)),
                sampleTodo(id = "today", dueDate = today),
                sampleTodo(id = "future", dueDate = today.plusDays(1)),
                sampleTodo(id = "done-today", dueDate = today, isCompleted = true)
            ),
            today = today
        )

        assertEquals(3, summary.openTasks)
        assertEquals(1, summary.dueToday)
        assertEquals(1, summary.overdue)
        assertEquals(1, summary.completed)
    }

    private fun sampleTodo(
        id: String,
        title: String = "Task",
        dueDate: LocalDate? = null,
        priority: Priority = Priority.MEDIUM,
        tags: List<String> = emptyList(),
        isCompleted: Boolean = false,
        recurrence: String = "none",
        filePath: String = ""
    ) = TodoItem(
        id = id,
        title = title,
        content = "notes",
        dueDate = dueDate,
        priority = priority,
        tags = tags,
        isCompleted = isCompleted,
        recurrence = recurrence,
        filePath = filePath,
        createdAt = LocalDateTime.of(2024, 1, 1, 0, 0)
    )
}

private class FakeTodoDataSource(
    private val todos: MutableList<TodoItem>,
    private val pullResult: GitRepository.GitResult = GitRepository.GitResult.Success,
    private val pushResult: GitRepository.GitResult = GitRepository.GitResult.Success,
    private val retryResult: GitRepository.GitResult = GitRepository.GitResult.Success
) : TodoDataSource {
    override val automaticSyncEvents = MutableSharedFlow<GitRepository.GitResult>(extraBufferCapacity = 1)
    val saved = mutableListOf<TodoItem>()
    val trash = mutableListOf<TodoItem>()
    var getTodosCalls = 0
    var pullCalls = 0
        private set

    override suspend fun getTodos(): List<TodoItem> {
        getTodosCalls++
        return todos.toList()
    }

    override suspend fun getWidgetTodoSnapshot(today: LocalDate): WidgetTodoSnapshot =
        WidgetTodoSnapshot.fromTodos(todos, today)

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
        val sourcePath = todo.filePath.removePrefix("Tasks/").ifBlank { "${todo.id}.md" }
        val trashed = todo.copy(filePath = "Tasks/.trash/$sourcePath")
        trash += trashed
        return trashed
    }

    override suspend fun restoreTodo(todo: TodoItem): TodoItem {
        val restoredPath = todo.filePath.removePrefix("Tasks/.trash/")
        val restored = todo.copy(filePath = "Tasks/$restoredPath")
        todos += restored
        trash.removeAll { it.id == todo.id }
        return restored
    }

    override suspend fun getTrashedTodos(): List<TodoItem> = trash.toList()

    override suspend fun purgeOldTrash(): Int = 0

    override suspend fun bulkSave(todos: List<TodoItem>) {
        todos.forEach { saveTodo(it) }
    }

    override suspend fun bulkTrash(todos: List<TodoItem>): List<TodoItem> {
        return todos.map { trashTodo(it) }
    }

    override suspend fun bulkRestore(todos: List<TodoItem>): List<TodoItem> {
        return todos.map { restoreTodo(it) }
    }

    override suspend fun pull(): GitRepository.GitResult {
        pullCalls++
        return pullResult
    }

    override suspend fun push(message: String): GitRepository.GitResult = pushResult

    override suspend fun retryPendingSync(): GitRepository.GitResult = retryResult

    override suspend fun resolveConflicts(
        files: List<String>,
        keepLocal: Boolean
    ): GitRepository.GitResult = GitRepository.GitResult.Success
}
