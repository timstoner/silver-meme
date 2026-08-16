package com.tmstoner.silvermeme.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.tmstoner.silvermeme.ui.components.TodoItemCard
import com.tmstoner.silvermeme.viewmodel.SortOrder
import com.tmstoner.silvermeme.viewmodel.SyncState
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import kotlinx.coroutines.launch

/**
 * Two-pane layout for tablet landscape mode (WindowWidthSizeClass ≥ Medium).
 *
 * Left pane  (~350 dp, fixed): permanent project/folder filter drawer + task list.
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
    val selectedIds       by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selectedTodoId    by viewModel.selectedTodoId.collectAsStateWithLifecycle()

    val isSelecting = selectedIds.isNotEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    var showFilterPanel  by remember { mutableStateOf(false) }
    var showSortMenu     by remember { mutableStateOf(false) }
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
                        title  = { Text(stringResource(R.string.app_name)) },
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
                            IconButton(onClick = { showFilterPanel = !showFilterPanel }) {
                                Icon(
                                    if (filterState.priority != null ||
                                        filterState.showCompleted ||
                                        filterState.showOverdue
                                    ) Icons.Outlined.FilterAlt else Icons.Filled.FilterList,
                                    contentDescription = "Filter"
                                )
                            }
                            IconButton(
                                onClick = { viewModel.syncFromRemote() },
                                enabled = syncState !is SyncState.Syncing
                            ) {
                                if (syncState is SyncState.Syncing) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.padding(8.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Sync from remote")
                                }
                            }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded         = showSortMenu,
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
                                    DropdownMenuItem(
                                        text        = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                        onClick     = { showSortMenu = false; onOpenSettings() }
                                    )
                                    DropdownMenuItem(
                                        text        = { Text("Trash") },
                                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                        onClick     = { showSortMenu = false; onOpenTrash() }
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

                // Filter chips (conditional)
                if (showFilterPanel) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
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
                                    label = { Text(p.label.replaceFirstChar { it.uppercaseChar() }) }
                                )
                            }
                        }
                    }
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
                    contentDescription = "Add new todo"
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

                    // ── Permanent project / folder drawer ──────────────────────
                    Surface(tonalElevation = 2.dp) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "Filter",
                                style    = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            NavigationDrawerItem(
                                label    = { Text("All projects") },
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
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                "Priority",
                                style    = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                            )
                            Priority.entries.forEach { p ->
                                NavigationDrawerItem(
                                    label    = { Text(p.label.replaceFirstChar { it.uppercaseChar() }) },
                                    selected = filterState.priority == p,
                                    onClick  = {
                                        viewModel.setFilterPriority(
                                            if (filterState.priority == p) null else p
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            NavigationDrawerItem(
                                label    = { Text("Show completed") },
                                icon     = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                                selected = filterState.showCompleted,
                                onClick  = { viewModel.setFilterCompleted(!filterState.showCompleted) },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            NavigationDrawerItem(
                                label    = { Text("Overdue only") },
                                selected = filterState.showOverdue,
                                onClick  = { viewModel.setFilterOverdue(!filterState.showOverdue) },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            NavigationDrawerItem(
                                label    = { Text("Reset filters") },
                                icon     = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                                selected = false,
                                onClick  = { viewModel.resetFilters() },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }

                    HorizontalDivider()

                    // ── Task list ──────────────────────────────────────────────
                    val isRefreshing = syncState is SyncState.Syncing
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh    = { viewModel.syncFromRemote() },
                        modifier     = Modifier.fillMaxSize()
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (uiState.isLoading) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            if (!uiState.isLoading && groups.isEmpty()) {
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "No todos found",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Tap + to create one, or pull to sync",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding      = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    groups.forEach { group ->
                                        item(
                                            key         = "header_${group.label}",
                                            contentType = "group_header"
                                        ) {
                                            Text(
                                                text     = "${group.label} (${group.todos.size})",
                                                style    = MaterialTheme.typography.titleSmall,
                                                color    = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                        items(
                                            items       = group.todos,
                                            key         = { it.id },
                                            contentType = { "todo_card" }
                                        ) { todo ->
                                            TodoItemCard(
                                                todo              = todo,
                                                onToggleComplete  = {
                                                    if (!isSelecting) viewModel.toggleComplete(it)
                                                },
                                                onClick           = { t ->
                                                    if (isSelecting) {
                                                        viewModel.toggleSelection(t.id)
                                                    } else {
                                                        newTaskRequested = false
                                                        viewModel.selectTodoForPane(t.id)
                                                    }
                                                },
                                                onLongClick       = { t ->
                                                    viewModel.toggleSelection(t.id)
                                                },
                                                isSelected        = todo.id in selectedIds,
                                                onSelectionToggle = if (isSelecting) {
                                                    { viewModel.toggleSelection(todo.id) }
                                                } else null,
                                                modifier          = Modifier.animateItem()
                                            )
                                        }
                                    }
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
