package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.ui.components.TodoItemCard
import com.tmstoner.silvermeme.ui.theme.SilvermemeTheme
import java.time.LocalDate

private val previewTodos = listOf(
    TodoItem(
        id = "prepare-demo",
        title = "Prepare tablet user-testing demo",
        dueDate = LocalDate.of(2026, 9, 28),
        priority = Priority.URGENT,
        location = "Office",
        filePath = "Tasks/Work/Prepare tablet user-testing demo.md"
    ),
    TodoItem(
        id = "review-feedback",
        title = "Review feedback from the last test session",
        dueDate = LocalDate.of(2026, 9, 25),
        priority = Priority.HIGH,
        recurrence = "weekly",
        filePath = "Tasks/Work/Review feedback from the last test session.md"
    ),
    TodoItem(
        id = "buy-coffee",
        title = "Buy coffee beans",
        dueDate = LocalDate.of(2026, 10, 1),
        priority = Priority.MEDIUM,
        location = "Market",
        filePath = "Tasks/Home/Buy coffee beans.md"
    ),
    TodoItem(
        id = "archive-notes",
        title = "Archive completed project notes",
        priority = Priority.LOW,
        isCompleted = true,
        filePath = "Tasks/Work/Archive completed project notes.md"
    )
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PreviewTodoListPane(
    modifier: Modifier = Modifier,
    showFilters: Boolean,
    onTodoClick: (TodoItem) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("SilverMeme") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Filter")
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Sync from remote")
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                    }
                )
                if (showFilters) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { FilterChip(selected = true, onClick = {}, label = { Text("All projects") }) }
                        item { FilterChip(selected = false, onClick = {}, label = { Text("Work") }) }
                        item { FilterChip(selected = false, onClick = {}, label = { Text("Home") }) }
                        item { FilterChip(selected = true, onClick = {}, label = { Text("Overdue") }) }
                        item { FilterChip(selected = false, onClick = {}, label = { Text("High") }) }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {}) {
                Icon(Icons.Filled.Add, contentDescription = "Add new todo")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Upcoming (3)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(previewTodos.take(3), key = { it.id }) { todo ->
                TodoItemCard(
                    todo = todo,
                    onToggleComplete = {},
                    onClick = onTodoClick,
                    onLongClick = {}
                )
            }
            item {
                Text(
                    text = "Completed (1)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(previewTodos.drop(3), key = { it.id }) { todo ->
                TodoItemCard(
                    todo = todo,
                    onToggleComplete = {},
                    onClick = onTodoClick,
                    onLongClick = {}
                )
            }
        }
    }
}

@Preview(name = "Phone todo list", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PhoneTodoListPreview() {
    SilvermemeTheme(dynamicColor = false) {
        PreviewTodoListPane(showFilters = false)
    }
}

@Preview(name = "Tablet landscape todo list", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun TabletTodoLayoutPreview() {
    SilvermemeTheme(dynamicColor = false) {
        Row(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .width(350.dp)
                    .fillMaxHeight(),
                tonalElevation = 1.dp
            ) {
                PreviewTodoListPane(showFilters = true)
            }
            VerticalDivider()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Prepare tablet user-testing demo",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Select a task to view or edit its details.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}



