package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.ui.components.TodoItemCard
import com.tmstoner.silvermeme.viewmodel.TodoGroup

/** Shared, state-hoisted task-list body for phone and tablet list panes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoTaskListContent(
    groups: List<TodoGroup>,
    isLoading: Boolean,
    hasAnyTodos: Boolean,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleComplete: (TodoItem) -> Unit,
    onTodoClick: (TodoItem) -> Unit,
    onTodoLongClick: (TodoItem) -> Unit,
    onSelectionToggle: (String) -> Unit,
    onResetFilters: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!isLoading && groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(
                            if (hasAnyTodos) R.string.empty_filter_title else R.string.empty_vault_title
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            if (hasAnyTodos) R.string.empty_filter_message else R.string.empty_vault_message
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasAnyTodos) {
                        TextButton(onClick = onResetFilters) {
                            Text(stringResource(R.string.action_clear_filters))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.forEach { group ->
                    stickyHeader(key = "header_${group.label}") {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.task_group_count,
                                    group.todos.size,
                                    localizedGroupTitle(group.label),
                                    group.todos.size
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                    items(group.todos, key = { it.id }, contentType = { "todo_card" }) { todo ->
                        TodoItemCard(
                            todo = todo,
                            onToggleComplete = { if (!selectionMode) onToggleComplete(it) },
                            onClick = onTodoClick,
                            onLongClick = onTodoLongClick,
                            isSelected = todo.id in selectedIds,
                            onSelectionToggle = if (selectionMode) {
                                { onSelectionToggle(todo.id) }
                            } else null,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun localizedGroupTitle(label: String): String = stringResource(
    when (label) {
        "Overdue" -> R.string.group_overdue
        "Today" -> R.string.group_today
        "Tomorrow" -> R.string.group_tomorrow
        "This Week" -> R.string.group_this_week
        "Next Week" -> R.string.group_next_week
        "Later" -> R.string.group_later
        else -> R.string.group_no_due_date
    }
)
