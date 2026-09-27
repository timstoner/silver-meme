package com.tmstoner.silvermeme.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
 * Main TODO list screen.
 *
 * Shows the list of all tasks with filtering, sorting, and sync controls.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoListScreen(
    viewModel: TodoViewModel,
    onAddTodo: () -> Unit,
    onEditTodo: (TodoItem) -> Unit,
    onOpenNavigationDrawer: () -> Unit
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val filterState       by viewModel.filterState.collectAsStateWithLifecycle()
    val syncState         by viewModel.syncState.collectAsStateWithLifecycle()
    val pendingSync       by viewModel.pendingSync.collectAsStateWithLifecycle()
    val groups            by viewModel.visibleGroups.collectAsStateWithLifecycle()
    val availableProjects by viewModel.availableProjects.collectAsStateWithLifecycle()
    val selectedIds       by viewModel.selectedIds.collectAsStateWithLifecycle()

    val isSelecting = selectedIds.isNotEmpty()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    var showFilterPanel    by remember { mutableStateOf(false) }
    var showSortMenu       by remember { mutableStateOf(false) }
    var showSearchBar      by remember { mutableStateOf(false) }
    var searchQuery        by remember { mutableStateOf("") }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictFiles      by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPriorityMenu   by remember { mutableStateOf(false) }

    // Exit selection mode on back press
    BackHandler(enabled = isSelecting) {
        viewModel.clearSelection()
    }

    // Transparent resync: pull from remote automatically when the list screen
    // is first shown, so the user never has to tap the manual sync button just
    // to see the latest remote changes.
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
            is SyncState.Failure -> Unit // SyncFailureFeedback owns retry and dismissal.
            is SyncState.Conflict -> {
                conflictFiles = (syncState as SyncState.Conflict).files
                showConflictDialog = true
                // Do not clearSyncState here — do it after user resolves
            }
            else -> Unit
        }
    }

    Scaffold(
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

                // Search bar
                AnimatedVisibility(visible = showSearchBar && !isSelecting) {
                    TodoSearchField(
                        query = searchQuery,
                        onQueryChange = { query ->
                            searchQuery = query
                            viewModel.onSearchInput(query)
                        },
                        onClear = {
                            searchQuery = ""
                            viewModel.onSearchInput("")
                        }
                    )
                }

                // Filter chips
                AnimatedVisibility(visible = showFilterPanel) {
                    TodoFilterControls(
                        state = filterState,
                        projects = availableProjects,
                        compact = true,
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
            FloatingActionButton(onClick = onAddTodo) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_task))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Fix 3: pull-to-refresh wraps all list content
            val isRefreshing = syncState is SyncState.Syncing
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
                        if (isSelecting) viewModel.toggleSelection(todo.id) else onEditTodo(todo)
                    },
                    onTodoLongClick = { todo -> viewModel.toggleSelection(todo.id) },
                    onSelectionToggle = viewModel::toggleSelection,
                    onResetFilters = viewModel::resetFilters,
                    contentPadding = PaddingValues(12.dp)
                )
            }

            // Inline error
            uiState.errorMessage?.let { msg ->
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = msg.ifBlank { context.getString(R.string.error_task_load) },
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = viewModel::loadTodos) {
                        Text(stringResource(R.string.action_reload_tasks))
                    }
                }
            }

            SyncFailureFeedback(
                syncState = syncState,
                snackbarHostState = snackbarHostState,
                onRetry = viewModel::retryFailedSync,
                onDismiss = viewModel::clearSyncState
            )
        }
    }

    // Conflict resolution dialog — shown when a pull/push results in a merge conflict
    if (showConflictDialog) {
        ConflictResolutionDialog(
            conflictFiles = conflictFiles,
            onKeepLocal = {
                showConflictDialog = false
                viewModel.resolveConflicts(conflictFiles, keepLocal = true)
            },
            onKeepRemote = {
                showConflictDialog = false
                viewModel.resolveConflicts(conflictFiles, keepLocal = false)
            },
            onDismiss = {
                showConflictDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.conflict_unresolved))
                }
            }
        )
    }
}
