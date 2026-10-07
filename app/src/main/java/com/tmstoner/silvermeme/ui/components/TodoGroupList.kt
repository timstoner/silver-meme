package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.viewmodel.TodoGroup
import com.tmstoner.silvermeme.viewmodel.TodoViewModel

/**
 * The grouped task list shared by the phone list screen and the tablet list pane:
 * loading indicator, empty state, today's capacity bar, group headers, and
 * swipeable [TodoItemCard] rows.
 *
 * While [selectedIds] is non-empty the list is in multi-select mode: swipes are
 * disabled and taps toggle selection instead of opening the task.
 *
 * @param todayLoe effort of open tasks due today; the capacity bar is hidden when 0.
 */
@Composable
fun TodoGroupList(
    groups: List<TodoGroup>,
    isLoading: Boolean,
    todayLoe: Int,
    selectedIds: Set<String>,
    onOpen: (TodoItem) -> Unit,
    onToggleComplete: (TodoItem) -> Unit,
    onTrash: (TodoItem) -> Unit,
    onToggleSelection: (TodoItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    itemSpacing: Dp = 8.dp
) {
    val isSelecting = selectedIds.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (!isLoading && groups.isEmpty()) {
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
                contentPadding      = contentPadding,
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                if (todayLoe > 0) {
                    item(key = "today_capacity", contentType = "capacity") {
                        CapacityBar(loe = todayLoe, capacity = TodoViewModel.DEFAULT_DAILY_CAPACITY)
                    }
                }
                groups.forEach { group ->
                    item(key = "header_${group.label}", contentType = "group_header") {
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
                        SwipeableTodoRow(
                            todo       = todo,
                            enabled    = !isSelecting,
                            onComplete = { onToggleComplete(todo) },
                            onTrash    = { onTrash(todo) },
                            modifier   = Modifier.animateItem()
                        ) {
                            TodoItemCard(
                                todo              = todo,
                                onToggleComplete  = { if (!isSelecting) onToggleComplete(it) },
                                onClick           = { t -> if (isSelecting) onToggleSelection(t) else onOpen(t) },
                                // Long-press enters selection mode, or toggles when already selecting
                                onLongClick       = onToggleSelection,
                                isSelected        = todo.id in selectedIds,
                                onSelectionToggle = if (isSelecting) {
                                    { onToggleSelection(todo) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shows the "Moved to trash" snackbar and calls [onUndo] if the user taps Undo.
 * Pair with [TodoViewModel.trashTodo] / [TodoViewModel.undoTrash].
 */
suspend fun SnackbarHostState.showTrashedWithUndo(onUndo: () -> Unit) {
    val result = showSnackbar(
        message     = "Moved to trash",
        actionLabel = "Undo",
        duration    = SnackbarDuration.Short
    )
    if (result == SnackbarResult.ActionPerformed) onUndo()
}

/**
 * Wraps a list row with swipe gestures: swipe right toggles completion,
 * swipe left moves the task to trash. Disabled during multi-select.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTodoRow(
    todo: TodoItem,
    enabled: Boolean,
    onComplete: () -> Unit,
    onTrash: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                // Snap back; the reloaded list decides whether the row stays visible.
                SwipeToDismissBoxValue.StartToEnd -> { onComplete(); false }
                // Row is removed optimistically by the ViewModel.
                SwipeToDismissBoxValue.EndToStart -> { onTrash(); true }
                else -> false
            }
        },
        positionalThreshold = { it * 0.4f }
    )

    SwipeToDismissBox(
        state                      = dismissState,
        modifier                   = modifier,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        backgroundContent          = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else                              -> Color.Transparent
            }
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart)
                    Alignment.CenterEnd else Alignment.CenterStart
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(if (todo.isCompleted) "Reopen" else "Complete",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    SwipeToDismissBoxValue.EndToStart -> Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Trash",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Filled.Delete, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    else -> Unit
                }
            }
        }
    ) {
        content()
    }
}
