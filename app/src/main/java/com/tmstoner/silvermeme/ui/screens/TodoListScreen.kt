package com.tmstoner.silvermeme.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.ui.components.ConflictResolutionDialog
import com.tmstoner.silvermeme.ui.components.FilterChipRow
import com.tmstoner.silvermeme.ui.components.QuickAddBar
import com.tmstoner.silvermeme.ui.components.TodoGroupList
import com.tmstoner.silvermeme.ui.components.showTrashedWithUndo
import com.tmstoner.silvermeme.viewmodel.SyncState
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import kotlinx.coroutines.launch

/**
 * Main TODO list screen.
 *
 * Shows the list of all tasks with filtering, sorting, and sync controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    viewModel: TodoViewModel,
    onAddTodo: () -> Unit,
    onEditTodo: (TodoItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val filterState       by viewModel.filterState.collectAsStateWithLifecycle()
    val syncState         by viewModel.syncState.collectAsStateWithLifecycle()
    val groups            by viewModel.visibleGroups.collectAsStateWithLifecycle()
    val availableProjects by viewModel.availableProjects.collectAsStateWithLifecycle()
    val selectedIds       by viewModel.selectedIds.collectAsStateWithLifecycle()
    val dailyCapacity     by viewModel.dailyCapacity.collectAsStateWithLifecycle()

    val isSelecting = selectedIds.isNotEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    var showOverflowMenu   by remember { mutableStateOf(false) }
    var showSearchBar      by remember { mutableStateOf(false) }
    var searchQuery        by remember { mutableStateOf("") }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictFiles      by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPriorityMenu   by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Exit selection mode on back press
    BackHandler(enabled = isSelecting) {
        viewModel.clearSelection()
    }

    // Transparent resync: pull from remote automatically when the list screen
    // is first shown, so the user never has to tap the manual sync button just
    // to see the latest remote changes.
    LaunchedEffect(Unit) {
        viewModel.syncFromRemote()
    }

    // Surface sync state as Snackbar messages
    LaunchedEffect(syncState) {
        when (syncState) {
            is SyncState.Success -> {
                scope.launch { snackbarHostState.showSnackbar((syncState as SyncState.Success).message) }
                viewModel.clearSyncState()
            }
            is SyncState.Failure -> {
                scope.launch { snackbarHostState.showSnackbar("Sync error: ${(syncState as SyncState.Failure).message}") }
                viewModel.clearSyncState()
            }
            is SyncState.Conflict -> {
                conflictFiles = (syncState as SyncState.Conflict).files
                showConflictDialog = true
                // Do not clearSyncState here — do it after user resolves
            }
            else -> Unit
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    stringResource(R.string.app_name),
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                NavigationDrawerItem(
                    label    = { Text("All tasks") },
                    icon     = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    selected = filterState.project == null,
                    onClick  = {
                        viewModel.setFilterProject(null)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                availableProjects.forEach { proj ->
                    NavigationDrawerItem(
                        label    = { Text(proj) },
                        icon     = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        selected = filterState.project == proj,
                        onClick  = {
                            viewModel.setFilterProject(proj)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label    = { Text("Trash") },
                    icon     = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    selected = false,
                    onClick  = {
                        scope.launch { drawerState.close() }
                        onOpenTrash()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label    = { Text("Settings") },
                    icon     = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    selected = false,
                    onClick  = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
    Scaffold(
        topBar = {
            Column {
                if (isSelecting) {
                    // Contextual top bar for bulk selection
                    TopAppBar(
                        title  = { Text("${selectedIds.size} selected") },
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
                            // Select all
                            IconButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                            }
                            // Mark complete
                            IconButton(onClick = {
                                viewModel.bulkComplete()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Marked ${selectedIds.size} as complete")
                                }
                            }) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Mark complete")
                            }
                            // Set priority
                            Box {
                                IconButton(onClick = { showPriorityMenu = true }) {
                                    Icon(Icons.Filled.SwapVert, contentDescription = "Set priority")
                                }
                                DropdownMenu(
                                    expanded = showPriorityMenu,
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
                            // Trash selection
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
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
                        }
                    },
                    actions = {
                        // Search
                        IconButton(onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) {
                                searchQuery = ""
                                viewModel.onSearchInput("")
                            }
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        // Overflow menu
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
                            }
                        }
                    }
                )
                } // end else (normal top bar)

                // Search bar
                AnimatedVisibility(visible = showSearchBar && !isSelecting) {
                    SearchBar(
                        query         = searchQuery,
                        onQueryChange = { q -> searchQuery = q; viewModel.onSearchInput(q) },
                        onSearch      = { viewModel.onSearchInput(it) },
                        active        = false,
                        onActiveChange = {},
                        placeholder   = { Text("Search todos...") },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {}
                }

                // Filter + sort chips (single home for list filters)
                if (!isSelecting) {
                    FilterChipRow(
                        filterState     = filterState,
                        onSortOrder     = viewModel::setSortOrder,
                        onToggleOverdue = { viewModel.setFilterOverdue(!filterState.showOverdue) },
                        onPriority      = { p ->
                            viewModel.setFilterPriority(if (filterState.priority == p) null else p)
                        },
                        onToggleDone    = { viewModel.setFilterCompleted(!filterState.showCompleted) },
                        onClear         = viewModel::clearChipFilters
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTodo) {
                Icon(Icons.Filled.Add, contentDescription = "Add new todo")
            }
        },
        bottomBar = {
            if (!isSelecting) {
                QuickAddBar(onAdd = { parsed ->
                    // New tasks land in the project currently being viewed, like the detail screen's picker.
                    viewModel.saveTodo(parsed.toTodoItem(project = filterState.project))
                    scope.launch { snackbarHostState.showSnackbar("Added \"${parsed.title}\"") }
                })
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
                // All open tasks due today, regardless of the active filters.
                val todayLoe = remember(uiState.todos) { viewModel.getTodayLoe() }
                TodoGroupList(
                    groups            = groups,
                    isLoading         = uiState.isLoading,
                    todayLoe          = todayLoe,
                    dailyCapacity     = dailyCapacity,
                    selectedIds       = selectedIds,
                    onOpen            = onEditTodo,
                    onToggleComplete  = viewModel::toggleComplete,
                    onTrash           = { todo ->
                        viewModel.trashTodo(todo)
                        scope.launch { snackbarHostState.showTrashedWithUndo(viewModel::undoTrash) }
                    },
                    onToggleSelection = { viewModel.toggleSelection(it.id) }
                )
            }

            // Inline error
            uiState.errorMessage?.let { msg ->
                Text(
                    text     = "Error: $msg",
                    color    = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
    } // end ModalNavigationDrawer

    // Conflict resolution dialog — shown when a pull/push results in a merge conflict
    if (showConflictDialog) {
        ConflictResolutionDialog(
            conflictFiles = conflictFiles,
            onKeepLocal = {
                showConflictDialog = false
                viewModel.clearSyncState()
                scope.launch { snackbarHostState.showSnackbar("Kept local version") }
            },
            onKeepRemote = {
                showConflictDialog = false
                viewModel.clearSyncState()
                viewModel.syncFromRemote()
                scope.launch { snackbarHostState.showSnackbar("Synced remote version") }
            },
            onDismiss = {
                showConflictDialog = false
                scope.launch { snackbarHostState.showSnackbar("Conflict unresolved — sync pending") }
            }
        )
    }
}

