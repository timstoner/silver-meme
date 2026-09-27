package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.ui.components.TodoItemCard
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import kotlinx.coroutines.launch

/**
 * Shows all tasks currently in Tasks/.trash/, with swipe-to-restore and
 * swipe-to-permanently-delete actions, plus an "Empty trash" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: TodoViewModel,
    onOpenNavigationDrawer: () -> Unit
) {
    val trashedTodos     by viewModel.trashedTodos.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    val context = LocalContext.current

    var confirmEmptyTrash by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        pluralStringResource(
                            R.plurals.trash_title_count,
                            trashedTodos.size,
                            trashedTodos.size
                        )
                    )
                },
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
                    if (trashedTodos.isNotEmpty()) {
                        IconButton(onClick = { confirmEmptyTrash = true }) {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = stringResource(R.string.trash_empty_action)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (trashedTodos.isEmpty()) {
                Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.height(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.trash_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.trash_auto_purge),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            stringResource(R.string.trash_swipe_hint),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(
                        items       = trashedTodos,
                        key         = { it.id },
                        contentType = { "trash_card" }
                    ) { todo ->
                        TrashItemRow(
                            todo      = todo,
                            onRestore = {
                                viewModel.restoreTodo(todo)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.trash_restore_message, todo.title)
                                    )
                                }
                            },
                            onDelete  = {
                                viewModel.deleteTodo(todo)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.trash_delete_message, todo.title)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Empty trash confirmation
    if (confirmEmptyTrash) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTrash = false },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text  = {
                Text(
                    pluralStringResource(
                        R.plurals.trash_empty_confirm_count,
                        trashedTodos.size,
                        trashedTodos.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmptyTrash = false
                    val items = trashedTodos.toList()
                    items.forEach { viewModel.deleteTodo(it) }
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.trash_empty_done)) }
                }) {
                    Text(stringResource(R.string.trash_delete_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmptyTrash = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashItemRow(
    todo: TodoItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onRestore(); true }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                else -> false
            }
        },
        positionalThreshold = { it * 0.4f }
    )

    // Reset after action so the composable doesn't stay dismissed
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state             = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else                             -> MaterialTheme.colorScheme.surface
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Filled.RestoreFromTrash
                else                             -> Icons.Filled.DeleteForever
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else                             -> Alignment.CenterEnd
            }
            Box(
                modifier           = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(vertical = 4.dp),
                contentAlignment   = alignment
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Icon(icon, contentDescription = stringResource(R.string.trash_restore_action), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.trash_restore_action), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text(stringResource(R.string.trash_delete_action), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium)
                        Icon(icon, contentDescription = stringResource(R.string.trash_delete_forever), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    ) {
        TodoItemCard(
            todo             = todo,
            onToggleComplete = { /* no-op in trash */ },
            onClick          = { /* no detail view from trash */ },
            onLongClick      = { onDelete() }
        )
    }
}
