package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.ui.components.ConflictResolutionDialog
import com.tmstoner.silvermeme.ui.components.TodoItemCard
import com.tmstoner.silvermeme.viewmodel.SortOrder
import com.tmstoner.silvermeme.viewmodel.SyncState
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import kotlinx.coroutines.launch

/**
 * Two-pane layout for tablet landscape mode (WindowWidthSizeClass ≥ Medium).
 *
 * Left pane  (~350 dp, fixed): task list with optional compact filters.
 * Right pane (remaining space): detail / edit form for the selected task, or an
 *             empty-state prompt when nothing is selected.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TabletTodoLayout(
    viewModel: TodoViewModel,
    onOpenNavigationDrawer: () -> Unit,
    onNavigateToDashboard: () -> Unit = {}
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val filterState       by viewModel.filterState.collectAsStateWithLifecycle()
    val syncState         by viewModel.syncState.collectAsStateWithLifecycle()
    val pendingSync       by viewModel.pendingSync.collectAsStateWithLifecycle()
    val groups            by viewModel.visibleGroups.collectAsStateWithLifecycle()
    val availableProjects by viewModel.availableProjects.collectAsStateWithLifecycle()
    val selectedIds       by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selectedTodoId    by viewModel.selectedTodoId.collectAsStateWithLifecycle()

    val isSelecting = selectedIds.isNotEmpty()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    var showFilterPanel  by remember { mutableStateOf(false) }
    var showSortMenu     by remember { mutableStateOf(false) }
    var showSearchBar    by remember { mutableStateOf(false) }
    var searchQuery      by remember { mutableStateOf("") }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictFiles    by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPriorityMenu by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var focusSearchRequested by remember { mutableStateOf(false) }

    // The task currently shown in the right pane.
    // null  = "new task" mode (triggered by FAB)
    // non-null = edit-existing mode
    // absent (pane not open) = represented by selectedTodoId == null AND newTaskRequested == false
    var newTaskRequested by remember { mutableStateOf(false) }
    var editingSelectedTodo by remember { mutableStateOf(false) }

    LaunchedEffect(showSearchBar, focusSearchRequested) {
        if (showSearchBar && focusSearchRequested) {
            searchFocusRequester.requestFocus()
            focusSearchRequested = false
        }
    }

    val selectedTodo: TodoItem? = selectedTodoId?.let { id ->
        uiState.todos.firstOrNull { it.id == id }
    }

    // Transparent remote sync on first composition
    LaunchedEffect(Unit) {
        viewModel.syncFromRemoteIfStale()
    }

    // Surface sync state as Snackbar messages
    LaunchedEffect(syncState) {
        when (syncState) {
            is SyncState.Success -> {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.sync_success))
                }
                viewModel.clearSyncState()
            }
            is SyncState.Failure -> Unit // SyncFailureFeedback provides a retry action.
            is SyncState.Conflict -> {
                conflictFiles = (syncState as SyncState.Conflict).files
                showConflictDialog = true
            }
            else -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape &&
                    !newTaskRequested && !editingSelectedTodo
                ) {
                    when {
                        selectedIds.isNotEmpty() -> viewModel.clearSelection()
                        selectedTodoId != null -> viewModel.selectTodoForPane(null)
                        else -> onNavigateToDashboard()
                    }
                    true
                } else false
            } else {
                when (event.key) {
                    Key.N -> {
                        if (newTaskRequested || editingSelectedTodo) {
                            false
                        } else {
                            viewModel.selectTodoForPane(null)
                            newTaskRequested = true
                            true
                        }
                    }
                    Key.F -> {
                        if (isSelecting) viewModel.clearSelection()
                        showSearchBar = true
                        focusSearchRequested = true
                        true
                    }
                    Key.R -> {
                        viewModel.syncFromRemote()
                        true
                    }
                    Key.One -> {
                        if (newTaskRequested || editingSelectedTodo) {
                            false
                        } else {
                            onNavigateToDashboard()
                            true
                        }
                    }
                    else -> false
                }
            }
        },
        topBar = {
            Column {
                TodoListTopBar(
                    selecting = isSelecting,
                    selectedCount = selectedIds.size,
                    canSelect = groups.isNotEmpty(),
                    filterActive = filterState.priority != null ||
                        filterState.showCompleted || filterState.showOverdue,
                    syncing = syncState is SyncState.Syncing,
                    onNavigate = onOpenNavigationDrawer,
                    onClearSelection = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onEnterSelection = {
                        groups.firstOrNull()?.todos?.firstOrNull()?.let {
                            viewModel.toggleSelection(it.id)
                        }
                    },
                    onComplete = {
                        val count = selectedIds.size
                        viewModel.bulkComplete()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.resources.getQuantityString(
                                    R.plurals.selection_complete_count,
                                    count,
                                    count
                                )
                            )
                        }
                    },
                    onSetPriority = viewModel::bulkSetPriority,
                    onTrash = {
                        viewModel.bulkTrash { count ->
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.resources.getQuantityString(
                                        R.plurals.bulk_trash_count,
                                        count,
                                        count
                                    ),
                                    actionLabel = context.getString(R.string.action_undo)
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoLastTrash()
                                }
                            }
                        }
                    },
                    onSearch = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) {
                            searchQuery = ""
                            viewModel.onSearchInput("")
                        }
                    },
                    onFilter = { showFilterPanel = !showFilterPanel },
                    onSync = viewModel::syncFromRemote,
                    onSort = viewModel::setSortOrder
                )

                if (syncState is SyncState.Conflict && !showConflictDialog) {
                    TextButton(onClick = { showConflictDialog = true }) {
                        Text(
                            pluralStringResource(
                                R.plurals.sync_conflict_count,
                                conflictFiles.size,
                                conflictFiles.size
                            )
                        )
                    }
                }
                PendingSyncBanner(
                    pending = pendingSync,
                    onRetry = viewModel::retryPendingSync
                )
                Text(
                    text = stringResource(R.string.keyboard_shortcuts_hint),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )

                // Search bar (conditional)
                if (showSearchBar && !isSelecting) {
                    TodoSearchField(
                        query = searchQuery,
                        onQueryChange = { query ->
                            searchQuery = query
                            viewModel.onSearchInput(query)
                        },
                        onClear = {
                            searchQuery = ""
                            viewModel.onSearchInput("")
                        },
                        modifier = Modifier
                            .focusRequester(searchFocusRequester)
                    )
                }

                SyncFailureFeedback(
                    syncState = syncState,
                    snackbarHostState = snackbarHostState,
                    onRetry = viewModel::retryFailedSync,
                    onDismiss = viewModel::clearSyncState
                )

                // Compact, horizontally scrolling filters keep the task list usable on landscape tablets.
                if (showFilterPanel) {
                    TodoFilterControls(
                        state = filterState,
                        projects = availableProjects,
                        compact = false,
                        onProject = viewModel::setFilterProject,
                        onCompleted = { viewModel.setFilterCompleted(!filterState.showCompleted) },
                        onOverdue = { viewModel.setFilterOverdue(!filterState.showOverdue) },
                        onPriority = { priority ->
                            viewModel.setFilterPriority(
                                if (filterState.priority == priority) null else priority
                            )
                        },
                        onReset = viewModel::resetFilters
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.selectTodoForPane(null)
                    newTaskRequested = true
                },
                modifier = Modifier.semantics {
                    contentDescription = context.getString(R.string.action_add_task)
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            // ── Left pane ─────────────────────────────────────────────────────
            Surface(
                modifier      = Modifier
                    .width(350.dp)
                    .fillMaxHeight(),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {


                    // ── Task list ──────────────────────────────────────────────
                    val isRefreshing = syncState is SyncState.Syncing
                    Box(Modifier.fillMaxSize()) {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh    = { viewModel.syncFromRemote() },
                            modifier     = Modifier.fillMaxSize()
                        ) {
                            TodoTaskListContent(
                                groups = groups,
                                isLoading = uiState.isLoading,
                                hasAnyTodos = uiState.todos.isNotEmpty(),
                                selectionMode = isSelecting,
                                selectedIds = selectedIds,
                                onToggleComplete = viewModel::toggleComplete,
                                onTodoClick = { todo ->
                                    if (isSelecting) {
                                        viewModel.toggleSelection(todo.id)
                                    } else {
                                        newTaskRequested = false
                                        editingSelectedTodo = false
                                        viewModel.selectTodoForPane(todo.id)
                                    }
                                },
                                onTodoLongClick = { todo -> viewModel.toggleSelection(todo.id) },
                                onSelectionToggle = viewModel::toggleSelection,
                                onResetFilters = viewModel::resetFilters,
                                contentPadding = PaddingValues(8.dp)
                            )
                        }
                        uiState.errorMessage?.let { message ->
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    message.ifBlank { context.getString(R.string.error_task_load) },
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(onClick = viewModel::loadTodos) {
                                    Text(stringResource(R.string.action_reload_tasks))
                                }
                            }
                        }
                    }
                }
            }

            // ── Divider ───────────────────────────────────────────────────────
            VerticalDivider()

            // ── Right pane ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when {
                    newTaskRequested -> {
                        // New-task form — suppress the TopAppBar back arrow (two-pane: no nav)
                        TodoDetailScreen(
                            viewModel    = viewModel,
                            existingTodo = null,
                            onBack       = {
                                newTaskRequested = false
                                // After save the ViewModel will have reloaded; keep pane open
                            }
                        )
                    }
                    selectedTodoId != null -> {
                        if (uiState.isLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (selectedTodo != null) {
                            if (editingSelectedTodo) {
                                TodoDetailScreen(
                                    viewModel = viewModel,
                                    existingTodo = selectedTodo,
                                    onBack = { editingSelectedTodo = false }
                                )
                            } else {
                                TodoViewScreen(
                                    todo = selectedTodo,
                                    onEdit = { editingSelectedTodo = true },
                                    onBack = { viewModel.selectTodoForPane(null) }
                                )
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.todo_not_found),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                    else -> {
                        // Empty-state prompt
                        Box(
                            modifier            = Modifier.fillMaxSize(),
                            contentAlignment    = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.empty_select_task),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(R.string.empty_create_task),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Conflict resolution dialog
    if (showConflictDialog) {
        ConflictResolutionDialog(
            conflictFiles = conflictFiles,
            onKeepLocal   = {
                showConflictDialog = false
                viewModel.resolveConflicts(conflictFiles, keepLocal = true)
            },
            onKeepRemote  = {
                showConflictDialog = false
                viewModel.resolveConflicts(conflictFiles, keepLocal = false)
            },
            onDismiss     = {
                showConflictDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.conflict_unresolved))
                }
            }
        )
    }
}
