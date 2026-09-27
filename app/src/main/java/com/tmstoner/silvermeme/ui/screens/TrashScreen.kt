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
import androidx.compose.ui.res.stringResource
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

    var confirmEmptyTrash by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash (${trashedTodos.size})") },
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
                            Icon(Icons.Filled.DeleteForever, contentDescription = "Empty trash")
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
                        "Trash is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Items are auto-purged after 30 days",
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
                            "Swipe right to restore · Swipe left to delete forever",
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
                                    snackbarHostState.showSnackbar("\"${todo.title}\" restored")
                                }
                            },
                            onDelete  = {
                                viewModel.deleteTodo(todo)
                                scope.launch {
                                    snackbarHostState.showSnackbar("\"${todo.title}\" permanently deleted")
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
            title = { Text("Empty trash?") },
            text  = { Text("All ${trashedTodos.size} item(s) will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmptyTrash = false
                    val items = trashedTodos.toList()
                    items.forEach { viewModel.deleteTodo(it) }
                    scope.launch { snackbarHostState.showSnackbar("Trash emptied") }
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmptyTrash = false }) { Text("Cancel") }
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
                        Icon(icon, contentDescription = "Restore", tint = MaterialTheme.colorScheme.primary)
                        Text("Restore", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text("Delete", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium)
                        Icon(icon, contentDescription = "Delete forever", tint = MaterialTheme.colorScheme.error)
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
