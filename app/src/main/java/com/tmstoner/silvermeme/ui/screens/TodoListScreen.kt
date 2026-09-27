package com.tmstoner.silvermeme.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
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
@OptIn(ExperimentalMaterial3Api::class)
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
    val groups            by viewModel.visibleGroups.collectAsStateWithLifecycle()
    val availableProjects by viewModel.availableProjects.collectAsStateWithLifecycle()
    val selectedIds       by viewModel.selectedIds.collectAsStateWithLifecycle()

    val isSelecting = selectedIds.isNotEmpty()

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
                    title  = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor    = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = onOpenNavigationDrawer) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.cd_open_navigation)
                            )
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
                        // Filter
                        IconButton(onClick = { showFilterPanel = !showFilterPanel }) {
                            Icon(
                                if (filterState.priority != null || filterState.showCompleted || filterState.showOverdue)
                                    Icons.Outlined.FilterAlt else Icons.Filled.FilterList,
                                contentDescription = "Filter"
                            )
                        }
                        // Refresh / Pull
                        IconButton(
                            onClick  = { viewModel.syncFromRemote() },
                            enabled  = syncState !is SyncState.Syncing
                        ) {
                            if (syncState is SyncState.Syncing) {
                                CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Sync from remote")
                            }
                        }
                        // Sort menu
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded        = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                Text(
                                    "Sort by",
                                    style    = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text    = { Text(order.label) },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            showSortMenu = false
                                        }
                                    )
                                }
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

                // Filter chips
                AnimatedVisibility(visible = showFilterPanel) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = filterState.project == null,
                                    onClick = { viewModel.setFilterProject(null) },
                                    label = { Text("All projects") }
                                )
                            }
                            items(availableProjects, key = { it }) { project ->
                                FilterChip(
                                    selected = filterState.project == project,
                                    onClick = { viewModel.setFilterProject(project) },
                                    label = { Text(project) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = filterState.showCompleted,
                                onClick  = { viewModel.setFilterCompleted(!filterState.showCompleted) },
                                label    = { Text("Show done") },
                                leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) }
                            )
                            FilterChip(
                                selected = filterState.showOverdue,
                                onClick  = { viewModel.setFilterOverdue(!filterState.showOverdue) },
                                label    = { Text("Overdue") }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Priority.entries.forEach { p ->
                                FilterChip(
                                    selected = filterState.priority == p,
                                    onClick  = {
                                        viewModel.setFilterPriority(
                                            if (filterState.priority == p) null else p
                                        )
                                    },
                                    label    = { Text(p.label.replaceFirstChar { it.uppercaseChar() }) }
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTodo) {
                Icon(Icons.Filled.Add, contentDescription = "Add new todo")
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
                // Fix 1: keep LazyColumn always mounted; LinearProgressIndicator replaces full spinner
                Column(modifier = Modifier.fillMaxSize()) {
                    if (uiState.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (!uiState.isLoading && groups.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "No todos found",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Tap + to create one, or pull to sync",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding      = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groups.forEach { group ->
                                // Fix 2: stable keys + contentType for group headers
                                item(key = "header_${group.label}", contentType = "group_header") {
                                    Text(
                                        text     = "${group.label} (${group.todos.size})",
                                        style    = MaterialTheme.typography.titleSmall,
                                        color    = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                // Fix 2: stable keys + contentType + animateItem for todo cards
                                items(
                                    items       = group.todos,
                                    key         = { it.id },
                                    contentType = { "todo_card" }
                                ) { todo ->
                                    TodoItemCard(
                                        todo             = todo,
                                        onToggleComplete = { if (!isSelecting) viewModel.toggleComplete(it) },
                                        onClick          = { t ->
                                            if (isSelecting) {
                                                viewModel.toggleSelection(t.id)
                                            } else {
                                                onEditTodo(t)
                                            }
                                        },
                                        onLongClick      = { target ->
                                            if (isSelecting) {
                                                // Already in selection mode — treat as toggle
                                                viewModel.toggleSelection(target.id)
                                            } else {
                                                // Enter selection mode with this item selected
                                                viewModel.toggleSelection(target.id)
                                            }
                                        },
                                        isSelected       = todo.id in selectedIds,
                                        onSelectionToggle = if (isSelecting) {
                                            { viewModel.toggleSelection(todo.id) }
                                        } else null,
                                        modifier         = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
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
