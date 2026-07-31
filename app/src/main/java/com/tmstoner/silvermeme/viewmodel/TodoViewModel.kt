package com.tmstoner.silvermeme.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.repository.GitRepository
import com.tmstoner.silvermeme.data.repository.TodoDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Manages UI state for the TODO list and detail screens.
 *
 * Exposes:
 *  - [uiState]   – the current [TodoUiState] consumed by Compose screens
 *  - [syncState] – status of background git sync operations
 */
class TodoViewModel(private val repository: TodoDataSource) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // ── Filter / sort ─────────────────────────────────────────────────────────

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        loadTodos()
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    fun loadTodos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val todos = repository.getTodos()
                _uiState.update { it.copy(todos = todos, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    /** Pulls from remote and refreshes the local list. */
    fun syncFromRemote() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing("Pulling from remote…")
            when (val result = repository.pull()) {
                is GitRepository.GitResult.Success -> {
                    loadTodos()
                    _syncState.value = SyncState.Success("Sync complete")
                }
                is GitRepository.GitResult.Error -> {
                    _syncState.value = SyncState.Failure(result.message)
                }
            }
        }
    }

    /** Pushes local changes to remote. */
    fun pushToRemote(message: String = "Update todos") {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing("Pushing to remote…")
            when (val result = repository.push(message)) {
                is GitRepository.GitResult.Success -> _syncState.value = SyncState.Success("Push complete")
                is GitRepository.GitResult.Error   -> _syncState.value = SyncState.Failure(result.message)
            }
        }
    }

    fun clearSyncState() {
        _syncState.value = SyncState.Idle
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun saveTodo(todo: TodoItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                repository.saveTodo(todo)
                loadTodos()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun toggleComplete(todo: TodoItem) {
        saveTodo(todo.withCompletion(!todo.isCompleted))
    }

    fun deleteTodo(todo: TodoItem) {
        viewModelScope.launch {
            try {
                repository.deleteTodo(todo)
                loadTodos()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    fun setFilterPriority(priority: Priority?) =
        _filterState.update { it.copy(priority = priority) }

    fun setFilterCompleted(showCompleted: Boolean) =
        _filterState.update { it.copy(showCompleted = showCompleted) }

    fun setFilterOverdue(showOverdue: Boolean) =
        _filterState.update { it.copy(showOverdue = showOverdue) }

    fun setSortOrder(order: SortOrder) =
        _filterState.update { it.copy(sortOrder = order) }

    fun setSearchQuery(query: String) =
        _filterState.update { it.copy(searchQuery = query) }

    /** Applies the current [FilterState] to [TodoUiState.todos]. */
    fun filteredTodos(): List<TodoItem> {
        val todos  = _uiState.value.todos
        val filter = _filterState.value
        val today  = LocalDate.now()

        return todos
            .filter { todo ->
                (filter.showCompleted || !todo.isCompleted) &&
                (filter.priority == null || todo.priority == filter.priority) &&
                (!filter.showOverdue || (todo.dueDate?.isBefore(today) == true)) &&
                (filter.searchQuery.isBlank() ||
                    todo.title.contains(filter.searchQuery, ignoreCase = true) ||
                    todo.content.contains(filter.searchQuery, ignoreCase = true) ||
                    todo.tags.any { it.contains(filter.searchQuery, ignoreCase = true) })
            }
            .let { list ->
                when (filter.sortOrder) {
                    SortOrder.DUE_DATE_ASC  -> list.sortedWith(DUE_ASC)
                    SortOrder.DUE_DATE_DESC -> list.sortedWith(DUE_DESC)
                    SortOrder.PRIORITY_DESC -> list.sortedByDescending { it.priority.sortOrder }
                    SortOrder.CREATED_DESC  -> list.sortedByDescending { it.createdAt }
                    SortOrder.TITLE_ASC     -> list.sortedBy { it.title.lowercase() }
                }
            }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val repository: TodoDataSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TodoViewModel(repository) as T
    }

    companion object {
        /** Nulls sort last (no due date → end of list). */
        private val DUE_ASC: Comparator<TodoItem> = Comparator { a, b ->
            when {
                a.dueDate == null && b.dueDate == null -> 0
                a.dueDate == null -> 1
                b.dueDate == null -> -1
                else -> a.dueDate!!.compareTo(b.dueDate!!)
            }
        }
        private val DUE_DESC: Comparator<TodoItem> = Comparator { a, b ->
            when {
                a.dueDate == null && b.dueDate == null -> 0
                a.dueDate == null -> 1
                b.dueDate == null -> -1
                else -> b.dueDate!!.compareTo(a.dueDate!!)
            }
        }
    }
}

// ── Supporting data classes ───────────────────────────────────────────────────

data class TodoUiState(
    val todos: List<TodoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

data class FilterState(
    val priority: Priority? = null,
    val showCompleted: Boolean = false,
    val showOverdue: Boolean = false,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.DUE_DATE_ASC
)

enum class SortOrder(val label: String) {
    DUE_DATE_ASC("Due date ↑"),
    DUE_DATE_DESC("Due date ↓"),
    PRIORITY_DESC("Priority"),
    CREATED_DESC("Newest first"),
    TITLE_ASC("Title A-Z")
}

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val message: String) : SyncState()
    data class Success(val message: String) : SyncState()
    data class Failure(val message: String) : SyncState()
}
