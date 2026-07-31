package com.tmstoner.silvermeme.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.repository.GitRepository
import com.tmstoner.silvermeme.data.repository.TodoDataSource
import com.tmstoner.silvermeme.data.storage.SettingsStore
import com.tmstoner.silvermeme.util.FilterStateSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Manages UI state for the TODO list and detail screens.
 *
 * Exposes:
 *  - [uiState]   – the current [TodoUiState] consumed by Compose screens
 *  - [syncState] – status of background git sync operations
 *
 * @param settingsStore optional store used to persist [FilterState] across app
 * restarts (Track F). Pass `null` in tests to skip persistence.
 */
class TodoViewModel(
    private val repository: TodoDataSource,
    private val settingsStore: SettingsStore? = null
) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // ── Selection state for bulk actions (Track B2) ────────────────────────────

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    // ── Filter / sort ─────────────────────────────────────────────────────────

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        restoreFilterState()
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
                is GitRepository.GitResult.Conflict -> {
                    _syncState.value = SyncState.Conflict(result.files)
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
                is GitRepository.GitResult.Conflict -> _syncState.value = SyncState.Conflict(result.files)
            }
        }
    }

    fun clearSyncState() {
        _syncState.value = SyncState.Idle
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun saveTodo(todo: TodoItem, previousFilePath: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                repository.saveTodo(todo, previousFilePath)
                loadTodos()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun toggleComplete(todo: TodoItem) {
        viewModelScope.launch {
            // Mark current task as complete
            saveTodo(todo.withCompletion(!todo.isCompleted))
            
            // If marking complete and recurrence is set, spawn next occurrence (Track E2)
            if (!todo.isCompleted && todo.recurrence != "none" && todo.dueDate != null) {
                val nextDueDate = when (todo.recurrence) {
                    "daily" -> todo.dueDate!!.plusDays(1)
                    "weekly" -> todo.dueDate!!.plusWeeks(1)
                    "monthly" -> todo.dueDate!!.plusMonths(1)
                    else -> null
                }
                
                if (nextDueDate != null) {
                    val nextOccurrence = todo.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        isCompleted = false,
                        dueDate = nextDueDate,
                        updatedAt = java.time.LocalDateTime.now()
                    )
                    saveTodo(nextOccurrence)
                }
            }
        }
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

    /** Applies [transform] to the current [FilterState] and persists the result (Track F). */
    private fun updateFilter(transform: (FilterState) -> FilterState) {
        _filterState.update(transform)
        persistFilterState(_filterState.value)
    }

    fun setFilterPriority(priority: Priority?) =
        updateFilter { it.copy(priority = priority) }

    fun setFilterCompleted(showCompleted: Boolean) =
        updateFilter { it.copy(showCompleted = showCompleted) }

    fun setFilterOverdue(showOverdue: Boolean) =
        updateFilter { it.copy(showOverdue = showOverdue) }

    fun setSortOrder(order: SortOrder) =
        updateFilter { it.copy(sortOrder = order) }

    fun setSearchQuery(query: String) =
        updateFilter { it.copy(searchQuery = query) }

    fun setFilterProject(project: String?) =
        updateFilter { it.copy(project = project) }

    /** Clears all active filters back to defaults (keeps sort order). */
    fun resetFilters() =
        updateFilter { FilterState(sortOrder = it.sortOrder) }

    /** True if [todo] matches the current [FilterState]. */
    private fun matchesFilter(todo: TodoItem, filter: FilterState, today: LocalDate): Boolean =
        (filter.showCompleted || !todo.isCompleted) &&
        (filter.priority == null || todo.priority == filter.priority) &&
        (!filter.showOverdue || (todo.dueDate?.isBefore(today) == true)) &&
        (filter.project == null || todo.project == filter.project) &&
        (filter.searchQuery.isBlank() ||
            todo.title.contains(filter.searchQuery, ignoreCase = true) ||
            todo.content.contains(filter.searchQuery, ignoreCase = true) ||
            todo.tags.any { it.contains(filter.searchQuery, ignoreCase = true) })

    private fun sortTodos(list: List<TodoItem>, order: SortOrder): List<TodoItem> = when (order) {
        SortOrder.DUE_DATE_ASC  -> list.sortedWith(DUE_ASC)
        SortOrder.DUE_DATE_DESC -> list.sortedWith(DUE_DESC)
        SortOrder.PRIORITY_DESC -> list.sortedByDescending { it.priority.sortOrder }
        SortOrder.CREATED_DESC  -> list.sortedByDescending { it.createdAt }
        SortOrder.TITLE_ASC     -> list.sortedBy { it.title.lowercase() }
    }

    /** Applies the current [FilterState] to [TodoUiState.todos]. */
    fun filteredTodos(): List<TodoItem> {
        val todos  = _uiState.value.todos
        val filter = _filterState.value
        val today  = LocalDate.now()

        val matched = todos.filter { matchesFilter(it, filter, today) }
        return sortTodos(matched, filter.sortOrder)
    }

    /**
     * Groups the filtered todo list into semantic due-date buckets (Track F):
     * Overdue → Today → Tomorrow → This Week → Next Week → Later → No Due Date.
     * Empty groups are omitted. Items within each group respect the active sort order.
     */
    fun groupedTodos(): List<TodoGroup> {
        val filter = _filterState.value
        val today  = LocalDate.now()
        val matched = _uiState.value.todos.filter { matchesFilter(it, filter, today) }

        val overdue  = mutableListOf<TodoItem>()
        val todayList = mutableListOf<TodoItem>()
        val tomorrow = mutableListOf<TodoItem>()
        val thisWeek = mutableListOf<TodoItem>()
        val nextWeek = mutableListOf<TodoItem>()
        val later    = mutableListOf<TodoItem>()
        val noDate   = mutableListOf<TodoItem>()

        matched.forEach { todo ->
            val due = todo.dueDate
            when {
                due == null -> noDate.add(todo)
                !todo.isCompleted && due.isBefore(today) -> overdue.add(todo)
                due.isEqual(today) -> todayList.add(todo)
                due.isEqual(today.plusDays(1)) -> tomorrow.add(todo)
                due.isAfter(today.plusDays(1)) && !due.isAfter(today.plusDays(7)) -> thisWeek.add(todo)
                due.isAfter(today.plusDays(7)) && !due.isAfter(today.plusDays(14)) -> nextWeek.add(todo)
                else -> later.add(todo)
            }
        }

        return listOfNotNull(
            overdue.takeIf { it.isNotEmpty() }?.let { TodoGroup("Overdue", sortTodos(it, filter.sortOrder)) },
            todayList.takeIf { it.isNotEmpty() }?.let { TodoGroup("Today", sortTodos(it, filter.sortOrder)) },
            tomorrow.takeIf { it.isNotEmpty() }?.let { TodoGroup("Tomorrow", sortTodos(it, filter.sortOrder)) },
            thisWeek.takeIf { it.isNotEmpty() }?.let { TodoGroup("This Week", sortTodos(it, filter.sortOrder)) },
            nextWeek.takeIf { it.isNotEmpty() }?.let { TodoGroup("Next Week", sortTodos(it, filter.sortOrder)) },
            later.takeIf { it.isNotEmpty() }?.let { TodoGroup("Later", sortTodos(it, filter.sortOrder)) },
            noDate.takeIf { it.isNotEmpty() }?.let { TodoGroup("No Due Date", sortTodos(it, filter.sortOrder)) }
        )
    }

    // ── Filter persistence (Track F) ────────────────────────────────────────────

    private fun restoreFilterState() {
        val store = settingsStore ?: return
        viewModelScope.launch {
            val serialized = store.lastFilterState.first()
            FilterStateSerializer.deserialize(serialized)?.let { restored ->
                _filterState.value = restored
            }
        }
    }

    private fun persistFilterState(state: FilterState) {
        val store = settingsStore ?: return
        viewModelScope.launch {
            store.setLastFilterState(FilterStateSerializer.serialize(state))
        }
    }

    // ── Projects (Track E3 / F) ──────────────────────────────────────────────────

    /** Returns the distinct, sorted set of non-empty project names currently in use. */
    fun getAvailableProjects(): List<String> =
        _uiState.value.todos.map { it.project }.filter { it.isNotBlank() }.distinct().sorted()

    // ── Level of Effort (LOE) capacity planning (Track F) ────────────────────────

    /** Sum of [TodoItem.loe] for all incomplete tasks due on [date]. */
    fun getLoeForDate(date: LocalDate): Int =
        _uiState.value.todos.filter { it.dueDate == date && !it.isCompleted }.sumOf { it.loe }

    /** Sum of LOE points due today. */
    fun getTodayLoe(): Int = getLoeForDate(LocalDate.now())

    /** Percentage of [dailyCapacity] consumed by today's LOE total (may exceed 100). */
    fun getLoeCapacityPercent(dailyCapacity: Int = DEFAULT_DAILY_CAPACITY): Float =
        if (dailyCapacity <= 0) 0f else (getTodayLoe().toFloat() / dailyCapacity.toFloat()) * 100f

    // ── Selection methods (Track B2 - Bulk actions) ────────────────────────────

    fun toggleSelection(todoId: String) {
        _selectedIds.update { current ->
            if (current.contains(todoId)) current - todoId else current + todoId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun selectAll() {
        _selectedIds.value = uiState.value.todos.map { it.id }.toSet()
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val repository: TodoDataSource,
        private val settingsStore: SettingsStore? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TodoViewModel(repository, settingsStore) as T
    }

    companion object {
        /** Recommended daily LOE capacity, roughly an 8-hour work day. */
        const val DEFAULT_DAILY_CAPACITY = 21

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
    val sortOrder: SortOrder = SortOrder.DUE_DATE_ASC,
    val project: String? = null
)

/** A semantic bucket of todos grouped by due date (Track F). */
data class TodoGroup(
    val label: String,
    val todos: List<TodoItem>
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
    data class Conflict(val files: List<String>) : SyncState()
}

