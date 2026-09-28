package com.tmstoner.silvermeme.viewmodel

import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.model.WidgetTodoSnapshot
import com.tmstoner.silvermeme.data.repository.GitRepository
import com.tmstoner.silvermeme.data.repository.TodoDataSource
import com.tmstoner.silvermeme.data.storage.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
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
    fun `initial render loads local data without remote sync`() = runTest {
        val source = DashboardSyncDataSource()
        DashboardViewModel(source, DashboardSettingsStore())

        advanceUntilIdle()

        assertTrue(source.getTodosCalls > 0)
        assertEquals(0, source.pullCalls)
        assertEquals(0, source.retryCalls)
    }

    @Test
    fun `sync action pulls, records success, and reloads local data when not pending`() = runTest {
        val source = DashboardSyncDataSource()
        val settings = DashboardSettingsStore()
        val viewModel = DashboardViewModel(source, settings)
        advanceUntilIdle()
        val initialReads = source.getTodosCalls

        viewModel.onAction(DashboardAction.Sync)
        advanceUntilIdle()

        assertEquals(1, source.pullCalls)
        assertEquals(0, source.retryCalls)
        assertEquals(initialReads + 1, source.getTodosCalls)
        assertNotNull(settings.lastRecordedSyncTime)
        assertFalse(viewModel.uiState.value.syncStatus.isRetrying)
        assertEquals(null, viewModel.uiState.value.syncStatus.error)
        assertTrue(viewModel.uiState.value.syncStatus.conflictFiles.isEmpty())
    }

    @Test
    fun `sync action retries pending work instead of pulling`() = runTest {
        val source = DashboardSyncDataSource(pending = true)
        val viewModel = DashboardViewModel(source, DashboardSettingsStore())
        advanceUntilIdle()

        viewModel.sync()
        advanceUntilIdle()

        assertEquals(0, source.pullCalls)
        assertEquals(1, source.retryCalls)
        assertFalse(viewModel.uiState.value.syncStatus.isRetrying)
    }

    @Test
    fun `sync action exposes pull errors and clears retry state`() = runTest {
        val source = DashboardSyncDataSource(pullResult = GitRepository.GitResult.Error("Offline"))
        val viewModel = DashboardViewModel(source, DashboardSettingsStore())
        advanceUntilIdle()

        viewModel.sync()
        advanceUntilIdle()

        assertEquals(1, source.pullCalls)
        assertFalse(viewModel.uiState.value.syncStatus.isRetrying)
        assertEquals("Offline", viewModel.uiState.value.syncStatus.error)
        assertTrue(viewModel.uiState.value.syncStatus.conflictFiles.isEmpty())
    }

    @Test
    fun `sync action exposes pull conflicts and clears errors`() = runTest {
        val source = DashboardSyncDataSource(
            pullResult = GitRepository.GitResult.Conflict(listOf("Tasks/Report.md"))
        )
        val viewModel = DashboardViewModel(source, DashboardSettingsStore())
        advanceUntilIdle()

        viewModel.sync()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.syncStatus.isRetrying)
        assertEquals(null, viewModel.uiState.value.syncStatus.error)
        assertEquals(listOf("Tasks/Report.md"), viewModel.uiState.value.syncStatus.conflictFiles)
    }
}

private class DashboardSyncDataSource(
    pending: Boolean = false,
    private val pullResult: GitRepository.GitResult = GitRepository.GitResult.Success,
    private val retryResult: GitRepository.GitResult = GitRepository.GitResult.Success
) : TodoDataSource {
    override val automaticSyncEvents = MutableSharedFlow<GitRepository.GitResult>()
    override val pendingSync = MutableStateFlow(pending)
    var getTodosCalls = 0
        private set
    var pullCalls = 0
        private set
    var retryCalls = 0
        private set

    override suspend fun getTodos(): List<TodoItem> {
        getTodosCalls++
        return emptyList()
    }

    override suspend fun getWidgetTodoSnapshot(today: LocalDate): WidgetTodoSnapshot =
        WidgetTodoSnapshot.fromTodos(emptyList(), today)

    override suspend fun saveTodo(todo: TodoItem, previousFilePath: String?): TodoItem = todo
    override suspend fun deleteTodo(todo: TodoItem) = Unit
    override suspend fun trashTodo(todo: TodoItem): TodoItem = todo
    override suspend fun restoreTodo(todo: TodoItem): TodoItem = todo
    override suspend fun getTrashedTodos(): List<TodoItem> = emptyList()
    override suspend fun purgeOldTrash(): Int = 0
    override suspend fun bulkSave(todos: List<TodoItem>) = Unit
    override suspend fun bulkTrash(todos: List<TodoItem>): List<TodoItem> = todos
    override suspend fun bulkRestore(todos: List<TodoItem>): List<TodoItem> = todos

    override suspend fun pull(): GitRepository.GitResult {
        pullCalls++
        return pullResult
    }

    override suspend fun push(message: String): GitRepository.GitResult = GitRepository.GitResult.Success
    override suspend fun retryPendingSync(): GitRepository.GitResult {
        retryCalls++
        return retryResult
    }

    override suspend fun resolveConflicts(
        files: List<String>,
        keepLocal: Boolean
    ): GitRepository.GitResult = GitRepository.GitResult.Success
}

private class DashboardSettingsStore : SettingsStore {
    override val gitRemoteUrl: Flow<String> = flowOf("https://example.com/vault.git")
    override val gitUsername: Flow<String> = flowOf("")
    override val gitToken: Flow<String> = flowOf("")
    override val vaultPath: Flow<String> = flowOf("/vault")
    override val authorName: Flow<String> = flowOf("")
    override val authorEmail: Flow<String> = flowOf("")
    override val themeMode: Flow<String> = flowOf("system")
    override val lastFilterState: Flow<String> = flowOf("")
    override val pendingSync: Flow<Boolean> = flowOf(false)
    var lastRecordedSyncTime: Long? = null
        private set

    override suspend fun setGitRemoteUrl(url: String) = Unit
    override suspend fun setGitUsername(username: String) = Unit
    override suspend fun setGitToken(token: String) = Unit
    override suspend fun setVaultPath(path: String) = Unit
    override suspend fun setAuthorName(name: String) = Unit
    override suspend fun setAuthorEmail(email: String) = Unit
    override suspend fun setThemeMode(mode: String) = Unit
    override suspend fun setLastFilterState(serialized: String) = Unit
    override suspend fun setPendingSync(pending: Boolean) = Unit
    override suspend fun setLastSuccessfulSyncTime(vaultPath: String, epochMillis: Long) {
        lastRecordedSyncTime = epochMillis
    }
}
