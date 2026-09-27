package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.LevelOfEffort
import com.tmstoner.silvermeme.viewmodel.FilterState
import com.tmstoner.silvermeme.viewmodel.SortOrder

@Composable
fun TodoFilterControls(
    state: FilterState,
    projects: List<String>,
    compact: Boolean,
    onProject: (String?) -> Unit,
    onCompleted: () -> Unit,
    onOverdue: () -> Unit,
    onPriority: (Priority) -> Unit,
    onReset: () -> Unit
) {
    if (compact) {
        Column {
            ProjectChipRow(state, projects, onProject)
            StatusFilterChips(state, onCompleted, onOverdue)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Priority.entries.toList(), key = { it.name }) { priority ->
                    FilterChip(
                        selected = state.priority == priority,
                        onClick = { onPriority(priority) },
                        label = { Text(priority.localizedLabel()) }
                    )
                }
            }
        }
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = state.project == null,
                    onClick = { onProject(null) },
                    label = { Text(stringResource(R.string.filter_all_projects)) }
                )
            }
            items(projects, key = { it }) { project ->
                FilterChip(
                    selected = state.project == project,
                    onClick = { onProject(project) },
                    label = { Text(project) }
                )
            }
            item {
                FilterChip(
                    selected = state.showCompleted,
                    onClick = onCompleted,
                    label = { Text(stringResource(R.string.filter_show_completed)) },
                    leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
                )
            }
            item {
                FilterChip(
                    selected = state.showOverdue,
                    onClick = onOverdue,
                    label = { Text(stringResource(R.string.filter_overdue)) }
                )
            }
            items(Priority.entries.toList(), key = { it.name }) { priority ->
                FilterChip(
                    selected = state.priority == priority,
                    onClick = { onPriority(priority) },
                    label = { Text(priority.localizedLabel()) }
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = onReset,
                    label = { Text(stringResource(R.string.action_reset_filters)) },
                    leadingIcon = { Icon(Icons.Filled.RestartAlt, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun ProjectChipRow(
    state: FilterState,
    projects: List<String>,
    onProject: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = state.project == null,
                onClick = { onProject(null) },
                label = { Text(stringResource(R.string.filter_all_projects)) }
            )
        }
        items(projects, key = { it }) { project ->
            FilterChip(
                selected = state.project == project,
                onClick = { onProject(project) },
                label = { Text(project) }
            )
        }
    }
}

@Composable
private fun StatusFilterChips(
    state: FilterState,
    onCompleted: () -> Unit,
    onOverdue: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.showCompleted,
            onClick = onCompleted,
            label = { Text(stringResource(R.string.filter_show_completed)) },
            leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
        )
        FilterChip(
            selected = state.showOverdue,
            onClick = onOverdue,
            label = { Text(stringResource(R.string.filter_overdue)) }
        )
    }
}

@Composable
fun Priority.localizedLabel(): String = stringResource(
    when (this) {
        Priority.LOW -> R.string.priority_low
        Priority.MEDIUM -> R.string.priority_medium
        Priority.HIGH -> R.string.priority_high
        Priority.URGENT -> R.string.priority_urgent
    }
)

@Composable
fun SortOrder.localizedLabel(): String = stringResource(
    when (this) {
        SortOrder.DUE_DATE_ASC -> R.string.sort_due_ascending
        SortOrder.DUE_DATE_DESC -> R.string.sort_due_descending
        SortOrder.PRIORITY_DESC -> R.string.sort_priority
        SortOrder.CREATED_DESC -> R.string.sort_newest
        SortOrder.TITLE_ASC -> R.string.sort_title
    }
)

@Composable
fun LevelOfEffort.localizedLabel(): String = stringResource(
    when (this) {
        LevelOfEffort.NONE -> R.string.loe_not_estimated
        LevelOfEffort.TRIVIAL -> R.string.loe_trivial
        LevelOfEffort.QUICK -> R.string.loe_quick
        LevelOfEffort.SMALL -> R.string.loe_small
        LevelOfEffort.MEDIUM -> R.string.loe_medium
        LevelOfEffort.LARGE -> R.string.loe_large
        LevelOfEffort.VERY_LARGE -> R.string.loe_very_large
    }
)
