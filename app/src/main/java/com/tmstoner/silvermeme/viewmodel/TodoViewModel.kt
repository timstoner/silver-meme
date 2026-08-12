package com.tmstoner.silvermeme.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.RecurrenceRule
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.repository.GitRepository
import com.tmstoner.silvermeme.data.repository.TodoDataSource
import com.tmstoner.silvermeme.data.storage.SettingsStore
import com.tmstoner.silvermeme.notifications.NotificationScheduler
import com.tmstoner.silvermeme.util.FilterStateSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val settingsStore: SettingsStore? = null,
    private val notificationScheduler: NotificationScheduler? = null
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

    // Raw search input — debounced before persisting to avoid per-keystroke DataStore writes.
    private val _searchInput = MutableStateFlow("")

    // ── Trash state ───────────────────────────────────────────────────────────

    private val _trashedTodos = MutableStateFlow<List<TodoItem>>(emptyList())
    val trashedTodos: StateFlow<List<TodoItem>> = _trashedTodos.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        restoreFilterState()
        loadTodos()
        loadTrash()
        // Purge trash items older than 30 days on startup.
        viewModelScope.launch { repository.purgeOldTrash() }
        // Debounce search input: persist to DataStore only after 250 ms of inactivity.
        viewModelScope.launch {
            _searchInput
                .debounce(250)
                .collect { query ->
                    updateFilter { it.copy(searchQuery = query) }
                }
        }
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

    private fun loadTrash() {
        viewModelScope.launch {
            try {
                _trashedTodos.value = repository.getTrashedTodos()
            } catch (_: Exception) {}
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
                doSave(todo, previousFilePath)
                // Schedule or cancel reminder based on completion/due-date state
                if (todo.isCompleted) {
                    notificationScheduler?.cancel(todo.id)
                } else if (todo.dueDate != null) {
                    notificationScheduler?.schedule(todo)
                } else {
                    notificationScheduler?.cancel(todo.id)
                }
                doLoad()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** Suspending save — for use inside coroutines that need sequencing. */
    private suspend fun doSave(todo: TodoItem, previousFilePath: String? = null) {
        repository.saveTodo(todo, previousFilePath)
    }

    /** Suspending load — for use inside coroutines that need sequencing. */
    private suspend fun doLoad() {
        val todos = repository.getTodos()
        _uiState.update { it.copy(todos = todos, isLoading = false) }
    }

    fun toggleComplete(todo: TodoItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val completed = todo.withCompletion(!todo.isCompleted)
                doSave(completed, completed.filePath.takeIf { it.isNotBlank() })

                // The completed/uncompleted task's own reminder is no longer relevant:
                // cancel it either way (re-scheduled below if un-completing with a due date).
                if (completed.isCompleted) {
                    notificationScheduler?.cancel(completed.id)
                } else if (completed.dueDate != null) {
                    notificationScheduler?.schedule(completed)
                } else {
                    notificationScheduler?.cancel(completed.id)
                }

                // If marking complete and recurrence is set, spawn next occurrence (Track E2).
                // Bug 3/4 fix: clear filePath so storage derives a fresh, collision-safe filename.
                if (!todo.isCompleted && todo.recurrence != "none" && todo.dueDate != null) {
                    val nextDueDate = RecurrenceRule.parse(todo.recurrence).nextDueDate(todo.dueDate!!)
                    if (nextDueDate != null) {
                        val nextOccurrence = todo.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            isCompleted = false,
                            dueDate = nextDueDate,
                            filePath = "",   // force fresh filename derivation
                            updatedAt = java.time.LocalDateTime.now()
                        )
                        doSave(nextOccurrence, null)
                        // Schedule the reminder for the next iteration's due date.
                        notificationScheduler?.schedule(nextOccurrence)
                    }
                }
                doLoad()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /**
     * Moves [todo] to trash (Tasks/.trash/) and refreshes both lists.
     * Call [undoTrash] within the snackbar action window to restore immediately.
     */
    fun trashTodo(todo: TodoItem) {
        viewModelScope.launch {
            try {
                repository.trashTodo(todo)
                notificationScheduler?.cancel(todo.id)
                loadTodos()
                loadTrash()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /** Hard-deletes [todo] permanently (for use from the Trash screen). */
    fun deleteTodo(todo: TodoItem) {
        viewModelScope.launch {
            try {
                repository.deleteTodo(todo)
                notificationScheduler?.cancel(todo.id)
                loadTodos()
                loadTrash()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /** Restores [todo] from trash back to Tasks/ and refreshes both lists. */
    fun restoreTodo(todo: TodoItem) {
        viewModelScope.launch {
            try {
                val restored = repository.restoreTodo(todo)
                // Reschedule reminder if it had a due date
                if (!restored.isCompleted && restored.dueDate != null) {
                    notificationScheduler?.schedule(restored)
                }
                loadTodos()
                loadTrash()
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

    /**
     * Called on each keystroke. Updates the displayed search text immediately;
     * debounced persistence to DataStore happens via [_searchInput].
     */
    fun onSearchInput(query: String) {
        _searchInput.value = query
        // Update filterState immediately for the search bar to reflect current text,
        // but skip DataStore write here — the debounced collector handles that.
        _filterState.update { it.copy(searchQuery = query) }
    }

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
    fun filteredTodos(): List<TodoItem> = computeVisibleTodos(_uiState.value, _filterState.value)

    /**
     * Groups the filtered todo list into semantic due-date buckets (Track F):
     * Overdue → Today → Tomorrow → This Week → Next Week → Later → No Due Date.
     * Empty groups are omitted. Items within each group respect the active sort order.
     */
    fun groupedTodos(): List<TodoGroup> = computeGroups(_uiState.value, _filterState.value)

    private fun computeVisibleTodos(state: TodoUiState, filter: FilterState): List<TodoItem> {
        val today = LocalDate.now()
        val matched = state.todos.filter { matchesFilter(it, filter, today) }
        return sortTodos(matched, filter.sortOrder)
    }

    private fun computeGroups(state: TodoUiState, filter: FilterState): List<TodoGroup> {
        val today  = LocalDate.now()
        val matched = state.todos.filter { matchesFilter(it, filter, today) }

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

    /**
     * Reactive view of the filtered todo list (Compose-observable). Unlike [filteredTodos],
     * this recomputes automatically whenever [uiState] or [filterState] changes, so the UI
     * updates immediately when the user changes a filter/folder/priority without any manual
     * refresh action.
     */
    val visibleTodos: StateFlow<List<TodoItem>> =
        combine(_uiState, _filterState) { state, filter -> computeVisibleTodos(state, filter) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Reactive, Compose-observable version of [groupedTodos]. See [visibleTodos]. */
    val visibleGroups: StateFlow<List<TodoGroup>> =
        combine(_uiState, _filterState) { state, filter -> computeGroups(state, filter) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Reactive distinct, sorted set of non-empty project names currently in use. */
    val availableProjects: StateFlow<List<String>> =
        _uiState.map { state ->
            state.todos.map { it.project }.filter { it.isNotBlank() }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Marks all selected todos as complete, then syncs once. Clears selection when done. */
    fun bulkComplete() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val ids = _selectedIds.value
                val updated = _uiState.value.todos
                    .filter { it.id in ids && !it.isCompleted }
                    .map { it.withCompletion(true) }
                if (updated.isNotEmpty()) {
                    repository.bulkSave(updated)
                    updated.forEach { notificationScheduler?.cancel(it.id) }
                    doLoad()
                }
                clearSelection()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** Moves all selected todos to trash, then syncs once. Clears selection when done. */
    fun bulkTrash() {
        viewModelScope.launch {
            try {
                val ids = _selectedIds.value
                val targets = _uiState.value.todos.filter { it.id in ids }
                if (targets.isNotEmpty()) {
                    repository.bulkTrash(targets)
                    targets.forEach { notificationScheduler?.cancel(it.id) }
                    doLoad()
                    loadTrash()
                }
                clearSelection()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /** Updates priority for all selected todos, then syncs once. Clears selection when done. */
    fun bulkSetPriority(priority: com.tmstoner.silvermeme.data.model.Priority) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val ids = _selectedIds.value
                val updated = _uiState.value.todos
                    .filter { it.id in ids }
                    .map { it.copy(priority = priority) }
                if (updated.isNotEmpty()) {
                    repository.bulkSave(updated)
                    doLoad()
                }
                clearSelection()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val repository: TodoDataSource,
        private val settingsStore: SettingsStore? = null,
        private val notificationScheduler: NotificationScheduler? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TodoViewModel(repository, settingsStore, notificationScheduler) as T
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

