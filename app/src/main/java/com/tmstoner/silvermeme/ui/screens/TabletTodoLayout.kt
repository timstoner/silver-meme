package com.tmstoner.silvermeme.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.ui.components.ConflictResolutionDialog
import com.tmstoner.silvermeme.ui.components.FilterChipRow
import com.tmstoner.silvermeme.ui.components.QuickAddBar
import com.tmstoner.silvermeme.ui.components.TodoGroupList
import com.tmstoner.silvermeme.ui.components.VaultSwitcher
import com.tmstoner.silvermeme.ui.components.showTrashedWithUndo
import com.tmstoner.silvermeme.viewmodel.SyncState
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import kotlinx.coroutines.launch

/**
 * Two-pane layout for tablet landscape mode (WindowWidthSizeClass ≥ Medium).
 *
 * Left pane  (~350 dp, fixed): project list, filter chip row, the shared
 *             [TodoGroupList] (swipe actions, capacity bar), and the quick-add bar.
 * Right pane (remaining space): detail / edit form for the selected task, or an
 *             empty-state prompt when nothing is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabletTodoLayout(
    viewModel: TodoViewModel,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val filterState       by viewModel.filterState.collectAsStateWithLifecycle()
    val syncState         by viewModel.syncState.collectAsStateWithLifecycle()
    val groups            by viewModel.visibleGroups.collectAsStateWithLifecycle()
    val availableProjects by viewModel.availableProjects.collectAsStateWithLifecycle()
    val vaults            by viewModel.vaults.collectAsStateWithLifecycle()
    val activeVaultId     by viewModel.activeVaultId.collectAsStateWithLifecycle()
    val selectedIds       by viewModel.selectedIds.collectAsStateWithLifecycle()
    val dailyCapacity     by viewModel.dailyCapacity.collectAsStateWithLifecycle()
    val selectedTodoId    by viewModel.selectedTodoId.collectAsStateWithLifecycle()

    val isSelecting = selectedIds.isNotEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSearchBar    by remember { mutableStateOf(false) }
    var searchQuery      by remember { mutableStateOf("") }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictFiles    by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPriorityMenu by remember { mutableStateOf(false) }

    // The task currently shown in the right pane.
    // null  = "new task" mode (triggered by FAB)
    // non-null = edit-existing mode
    // absent (pane not open) = represented by selectedTodoId == null AND newTaskRequested == false
    var newTaskRequested by remember { mutableStateOf(false) }

    val selectedTodo: TodoItem? = selectedTodoId?.let { id ->
        uiState.todos.firstOrNull { it.id == id }
    }

    // Exit selection mode on back press
    BackHandler(enabled = isSelecting) {
        viewModel.clearSelection()
    }

    // Trash a task, closing the detail pane if it was showing that task.
    fun trashWithUndo(todo: TodoItem) {
        if (selectedTodoId == todo.id) viewModel.selectTodoForPane(null)
        viewModel.trashTodo(todo)
        scope.launch { snackbarHostState.showTrashedWithUndo(viewModel::undoTrash) }
    }

    // Transparent remote sync on first composition
    LaunchedEffect(Unit) {
        viewModel.syncFromRemote()
    }

    // Surface sync state as Snackbar messages
    LaunchedEffect(syncState) {
        when (syncState) {
            is SyncState.Success -> {
                scope.launch {
                    snackbarHostState.showSnackbar((syncState as SyncState.Success).message)
                }
                viewModel.clearSyncState()
            }
            is SyncState.Failure -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Sync error: ${(syncState as SyncState.Failure).message}"
                    )
                }
                viewModel.clearSyncState()
            }
            is SyncState.Conflict -> {
                conflictFiles = (syncState as SyncState.Conflict).files
                showConflictDialog = true
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            Column {
                if (isSelecting) {
                    TopAppBar(
                        title = { Text("${selectedIds.size} selected") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor    = MaterialTheme.colorScheme.secondaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Exit selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                            }
                            IconButton(onClick = {
                                viewModel.bulkComplete()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Marked ${selectedIds.size} as complete")
                                }
                            }) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Mark complete")
                            }
                            Box {
                                IconButton(onClick = { showPriorityMenu = true }) {
                                    Icon(Icons.Filled.SwapVert, contentDescription = "Set priority")
                                }
                                DropdownMenu(
                                    expanded         = showPriorityMenu,
                                    onDismissRequest = { showPriorityMenu = false }
                                ) {
                                    Text(
                                        "Set priority",
                                        style    = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                    Priority.entries.forEach { p ->
                                        DropdownMenuItem(
                                            text    = { Text(p.label.replaceFirstChar { it.uppercaseChar() }) },
                                            onClick = {
                                                showPriorityMenu = false
                                                viewModel.bulkSetPriority(p)
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = {
                                val count = selectedIds.size
                                viewModel.bulkTrash()
                                scope.launch {
                                    snackbarHostState.showSnackbar("$count items moved to trash")
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Move to trash")
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title  = { Text(filterState.project ?: stringResource(R.string.app_name)) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor    = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        actions = {
                            IconButton(onClick = {
                                showSearchBar = !showSearchBar
                                if (!showSearchBar) {
                                    searchQuery = ""
                                    viewModel.onSearchInput("")
                                }
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded         = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text        = { Text("Sync now") },
                                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                        enabled     = syncState !is SyncState.Syncing,
                                        onClick     = { showOverflowMenu = false; viewModel.syncFromRemote() }
                                    )
                                    DropdownMenuItem(
                                        text        = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                        onClick     = { showOverflowMenu = false; onOpenSettings() }
                                    )
                                    DropdownMenuItem(
                                        text        = { Text("Trash") },
                                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                        onClick     = { showOverflowMenu = false; onOpenTrash() }
                                    )
                                }
                            }
                        }
                    )
                }

                // Search bar (conditional)
                if (showSearchBar && !isSelecting) {
                    SearchBar(
                        query          = searchQuery,
                        onQueryChange  = { q -> searchQuery = q; viewModel.onSearchInput(q) },
                        onSearch       = { viewModel.onSearchInput(it) },
                        active         = false,
                        onActiveChange = {},
                        placeholder    = { Text("Search todos...") },
                        modifier       = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {}
                }
            }
        },
        floatingActionButton = {
            // Hidden while a form is open: the detail pane's own Save FAB sits in the
            // same corner and would be covered. Quick-add still works meanwhile.
            if (!newTaskRequested && selectedTodoId == null) {
                FloatingActionButton(
                    onClick = {
                        viewModel.selectTodoForPane(null)
                        newTaskRequested = true
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Add new todo"
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
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

                    // ── Projects (the tablet's always-visible navigation) ──────
                    Surface(tonalElevation = 2.dp) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp)
                        ) {
                            // Only shown with 2+ vaults (G2)
                            VaultSwitcher(
                                vaults        = vaults,
                                activeVaultId = activeVaultId,
                                onSwitch      = viewModel::switchVault
                            )
                            NavigationDrawerItem(
                                label    = { Text("All tasks") },
                                icon     = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                selected = filterState.project == null,
                                onClick  = { viewModel.setFilterProject(null) },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            availableProjects.forEach { proj ->
                                NavigationDrawerItem(
                                    label    = { Text(proj) },
                                    icon     = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                    selected = filterState.project == proj,
                                    onClick  = { viewModel.setFilterProject(proj) },
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // ── Filter + sort chips (same row as the phone list) ───────
                    if (!isSelecting) {
                        FilterChipRow(
                            filterState     = filterState,
                            onSortOrder     = viewModel::setSortOrder,
                            onToggleOverdue = { viewModel.setFilterOverdue(!filterState.showOverdue) },
                            onPriority      = { p ->
                                viewModel.setFilterPriority(if (filterState.priority == p) null else p)
                            },
                            onToggleDone    = { viewModel.setFilterCompleted(!filterState.showCompleted) },
                            onClear         = viewModel::clearChipFilters,
                            modifier        = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // ── Task list ──────────────────────────────────────────────
                    val isRefreshing = syncState is SyncState.Syncing
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh    = { viewModel.syncFromRemote() },
                        modifier     = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // All open tasks due today, regardless of the active filters.
                        val todayLoe = remember(uiState.todos) { viewModel.getTodayLoe() }
                        TodoGroupList(
                            groups            = groups,
                            isLoading         = uiState.isLoading,
                            todayLoe          = todayLoe,
                            dailyCapacity     = dailyCapacity,
                            selectedIds       = selectedIds,
                            onOpen            = { t ->
                                newTaskRequested = false
                                viewModel.selectTodoForPane(t.id)
                            },
                            onToggleComplete  = viewModel::toggleComplete,
                            onTrash           = ::trashWithUndo,
                            onToggleSelection = { viewModel.toggleSelection(it.id) },
                            contentPadding    = PaddingValues(8.dp),
                            itemSpacing       = 6.dp
                        )
                    }

                    // ── Quick add ──────────────────────────────────────────────
                    if (!isSelecting) {
                        QuickAddBar(onAdd = { parsed ->
                            // New tasks land in the project currently being viewed.
                            viewModel.saveTodo(parsed.toTodoItem(project = filterState.project))
                            scope.launch { snackbarHostState.showSnackbar("Added \"${parsed.title}\"") }
                        })
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
                        } else {
                            TodoDetailScreen(
                                viewModel    = viewModel,
                                existingTodo = selectedTodo,
                                onBack       = {
                                    viewModel.selectTodoForPane(null)
                                }
                            )
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
                                    "Select a task to view details",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Or tap + to create a new one",
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
                viewModel.clearSyncState()
                scope.launch { snackbarHostState.showSnackbar("Kept local version") }
            },
            onKeepRemote  = {
                showConflictDialog = false
                viewModel.clearSyncState()
                viewModel.syncFromRemote()
                scope.launch { snackbarHostState.showSnackbar("Synced remote version") }
            },
            onDismiss     = {
                showConflictDialog = false
                scope.launch { snackbarHostState.showSnackbar("Conflict unresolved — sync pending") }
            }
        )
    }
}
