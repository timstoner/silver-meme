package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.viewmodel.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListTopBar(
    selecting: Boolean,
    selectedCount: Int,
    canSelect: Boolean,
    filterActive: Boolean,
    syncing: Boolean,
    onNavigate: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onEnterSelection: () -> Unit,
    onComplete: () -> Unit,
    onSetPriority: (Priority) -> Unit,
    onTrash: () -> Unit,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    onSync: () -> Unit,
    onSort: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPriorityMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    if (selecting) {
        TopAppBar(
            modifier = modifier,
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.selected_task_count,
                        selectedCount,
                        selectedCount
                    )
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_exit_selection))
                }
            },
            actions = {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                }
                IconButton(onClick = onComplete) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.action_mark_complete))
                }
                Box {
                    IconButton(onClick = { showPriorityMenu = true }) {
                        Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.action_set_priority))
                    }
                    DropdownMenu(
                        expanded = showPriorityMenu,
                        onDismissRequest = { showPriorityMenu = false }
                    ) {
                        Priority.entries.forEach { priority ->
                            DropdownMenuItem(
                                text = { Text(priority.localizedLabel()) },
                                onClick = {
                                    showPriorityMenu = false
                                    onSetPriority(priority)
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onTrash) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_move_to_trash))
                }
            }
        )
    } else {
        TopAppBar(
            modifier = modifier,
            title = { Text(stringResource(R.string.app_name)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            navigationIcon = {
                IconButton(onClick = onNavigate) {
                    Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_open_navigation))
                }
            },
            actions = {
                IconButton(onClick = onEnterSelection, enabled = canSelect) {
                    Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.action_select_tasks))
                }
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                }
                IconButton(onClick = onFilter) {
                    Icon(
                        if (filterActive) Icons.Outlined.FilterAlt else Icons.Filled.FilterList,
                        contentDescription = stringResource(R.string.action_filter)
                    )
                }
                IconButton(onClick = onSync, enabled = !syncing) {
                    if (syncing) {
                        CircularProgressIndicator(modifier = Modifier, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_sync_remote))
                    }
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.localizedLabel()) },
                                onClick = {
                                    showSortMenu = false
                                    onSort(order)
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}
