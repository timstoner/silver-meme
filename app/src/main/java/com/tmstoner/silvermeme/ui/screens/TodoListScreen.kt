package com.tmstoner.silvermeme.ui.screens

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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
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
    onOpenSettings: () -> Unit
) {
    val uiState      by viewModel.uiState.collectAsState()
    val filterState  by viewModel.filterState.collectAsState()
    val syncState    by viewModel.syncState.collectAsState()
    val todos           = viewModel.filteredTodos()
    val groups          = viewModel.groupedTodos()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    var showDeleteDialog   by remember { mutableStateOf<TodoItem?>(null) }
    var showFilterPanel    by remember { mutableStateOf(false) }
    var showSortMenu       by remember { mutableStateOf(false) }
    var showSearchBar      by remember { mutableStateOf(false) }
    var searchQuery        by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val availableProjects = viewModel.getAvailableProjects()

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
            else -> Unit
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Filter",
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                NavigationDrawerItem(
                    label    = { Text("All projects") },
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
                Text(
                    "Priority",
                    style    = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Priority.entries.forEach { p ->
                    NavigationDrawerItem(
                        label    = { Text(p.label.replaceFirstChar { it.uppercaseChar() }) },
                        selected = filterState.priority == p,
                        onClick  = {
                            viewModel.setFilterPriority(if (filterState.priority == p) null else p)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label    = { Text("Show completed") },
                    icon     = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                    selected = filterState.showCompleted,
                    onClick  = {
                        viewModel.setFilterCompleted(!filterState.showCompleted)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label    = { Text("Overdue only") },
                    selected = filterState.showOverdue,
                    onClick  = {
                        viewModel.setFilterOverdue(!filterState.showOverdue)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label    = { Text("Reset filters") },
                    icon     = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                    selected = false,
                    onClick  = {
                        viewModel.resetFilters()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title  = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor    = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open filters")
                        }
                    },
                    actions = {
                        // Search
                        IconButton(onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) {
                                searchQuery = ""
                                viewModel.setSearchQuery("")
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
                                DropdownMenuItem(
                                    text    = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                    onClick = { showSortMenu = false; onOpenSettings() }
                                )
                            }
                        }
                    }
                )

                // Search bar
                AnimatedVisibility(visible = showSearchBar) {
                    SearchBar(
                        query         = searchQuery,
                        onQueryChange = { q -> searchQuery = q; viewModel.setSearchQuery(q) },
                        onSearch      = { viewModel.setSearchQuery(it) },
                        active        = false,
                        onActiveChange = {},
                        placeholder   = { Text("Search todos…") },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {}
                }

                // Filter chips
                AnimatedVisibility(visible = showFilterPanel) {
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
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                todos.isEmpty() -> {
                    Column(
                        modifier            = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groups.forEach { group ->
                            item(key = "header_${group.label}") {
                                Text(
                                    text     = "${group.label} (${group.todos.size})",
                                    style    = MaterialTheme.typography.titleSmall,
                                    color    = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(items = group.todos, key = { it.id }) { todo ->
                                TodoItemCard(
                                    todo             = todo,
                                    onToggleComplete = { viewModel.toggleComplete(it) },
                                    onClick          = { onEditTodo(it) },
                                    onLongClick      = { showDeleteDialog = it }
                                )
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
    } // end ModalNavigationDrawer

    // Delete confirmation dialog
    showDeleteDialog?.let { todo ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title   = { Text("Delete task?") },
            text    = { Text("\"${todo.title}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTodo(todo)
                    showDeleteDialog = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}
