package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.RecurrenceRule
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.ui.theme.SilvermemeTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoViewScreen(
    todo: TodoItem,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_task_details)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.action_edit_task))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            item {
                Text(
                    text = stringResource(
                        if (todo.isCompleted) R.string.task_status_completed else R.string.task_status_open
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                TaskMetadataCard(
                    label = stringResource(R.string.label_priority),
                    value = todo.priority.localizedLabel()
                )
            }
            todo.dueDate?.let { dueDate ->
                item {
                    TaskMetadataCard(
                        label = stringResource(R.string.label_due_date),
                        value = dueDate.format(
                            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                .withLocale(Locale.getDefault())
                        )
                    )
                }
            }
            item {
                TaskMetadataCard(
                    label = stringResource(R.string.label_reminder),
                    value = when {
                        !todo.reminderEnabled -> stringResource(R.string.reminder_disabled)
                        todo.reminderTime == null -> stringResource(R.string.reminder_due_midnight)
                        else -> todo.reminderTime.format(
                            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                                .withLocale(Locale.getDefault())
                        )
                    }
                )
            }
            todo.project.takeIf { it.isNotBlank() }?.let { project ->
                item {
                    TaskMetadataCard(
                        label = stringResource(R.string.label_project),
                        value = project
                    )
                }
            }
            todo.location?.takeIf { it.isNotBlank() }?.let { location ->
                item {
                    TaskMetadataCard(
                        label = stringResource(R.string.label_location),
                        value = location
                    )
                }
            }
            todo.recurrence.takeIf { it != "none" }?.let { recurrence ->
                item {
                    TaskMetadataCard(
                        label = stringResource(R.string.label_schedule),
                        value = RecurrenceRule.parse(recurrence).localizedSummary()
                    )
                }
            }
            if (todo.loe > 0) {
                item {
                    TaskMetadataCard(
                        label = stringResource(R.string.label_loe),
                        value = todo.loe.toString()
                    )
                }
            }
            if (todo.tags.isNotEmpty()) {
                item {
                    TaskMetadataCard(
                        label = stringResource(R.string.label_tags),
                        value = todo.tags.joinToString(separator = "  ") { "#$it" }
                    )
                }
            }
            if (todo.checklist.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.label_checklist),
                                style = MaterialTheme.typography.titleSmall
                            )
                            todo.checklist.forEach { item ->
                                Text(
                                    text = "${if (item.isDone) "✓" else "○"} ${item.text}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_notes),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = todo.content.ifBlank { stringResource(R.string.task_no_notes) },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskMetadataCard(
    label: String,
    value: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodoViewScreenPreview() {
    SilvermemeTheme(dynamicColor = false) {
        TodoViewScreen(
            todo = TodoItem(
                id = "preview",
                title = "Prepare project proposal",
                content = "Review the timeline and budget before the planning meeting.",
                dueDate = LocalDate.of(2026, 10, 2),
                location = "Office",
                tags = listOf("work", "planning"),
                filePath = "Tasks/Work/Prepare project proposal.md"
            ),
            onEdit = {},
            onBack = {}
        )
    }
}
