package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.ui.theme.StatusDone
import com.tmstoner.silvermeme.ui.theme.StatusOverdue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A card that summarises a single [TodoItem] with:
 *  - completion toggle
 *  - title (struck-through when done)
 *  - compact due-date/overdue status
 *  - optional location and recurrence indicator
 *  - priority chip, right-aligned at the end of the row
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoItemCard(
    todo: TodoItem,
    onToggleComplete: (TodoItem) -> Unit,
    onClick: (TodoItem) -> Unit,
    onLongClick: (TodoItem) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onSelectionToggle: (() -> Unit)? = null
) {
    val today   = LocalDate.now()
    val overdue = !todo.isCompleted && todo.dueDate != null && todo.dueDate.isBefore(today)
    val overdueDays = if (overdue) ChronoUnit.DAYS.between(todo.dueDate, today) else 0L
    val cardActionLabel = stringResource(
        if (onSelectionToggle == null) R.string.cd_open_task else R.string.cd_toggle_task_selection
    )
    val cardLongActionLabel = stringResource(R.string.cd_select_task)
    val statusDescription = when {
        isSelected -> stringResource(R.string.state_task_selected)
        todo.isCompleted -> stringResource(R.string.task_status_completed)
        overdue -> stringResource(R.string.state_task_overdue)
        else -> stringResource(R.string.task_status_open)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = cardActionLabel,
                onLongClickLabel = cardLongActionLabel,
                role = Role.Button,
                onClick     = { 
                    if (onSelectionToggle != null) {
                        onSelectionToggle()
                    } else {
                        onClick(todo)
                    }
                },
                onLongClick = { onLongClick(todo) }
            )
            .semantics(mergeDescendants = true) {
                stateDescription = statusDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                overdue -> StatusOverdue.copy(alpha = 0.08f)
                todo.isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (todo.isCompleted) 0.dp else 2.dp)
    ) {
        Row(
            modifier        = Modifier.padding(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Completion toggle
            IconButton(
                onClick = { onToggleComplete(todo) }
            ) {
                Icon(
                    imageVector        = if (todo.isCompleted) Icons.Filled.CheckCircle
                                        else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = stringResource(
                        if (todo.isCompleted) R.string.cd_mark_task_incomplete else R.string.cd_mark_task_complete
                    ),
                    tint               = if (todo.isCompleted) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // Title row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text           = todo.title,
                        modifier       = Modifier.weight(1f),
                        style          = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
                        color          = if (todo.isCompleted) StatusDone else MaterialTheme.colorScheme.onSurface,
                        maxLines       = 2,
                        overflow       = TextOverflow.Ellipsis
                    )
                    if (todo.recurrence != "none") {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector        = Icons.Filled.Repeat,
                            contentDescription = stringResource(R.string.label_schedule),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Compact due-date status; overdue text replaces the separate warning row.
                todo.dueDate?.let { due ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Outlined.CalendarToday,
                            contentDescription = stringResource(
                                if (overdue) R.string.overdue_description else R.string.due_date_description
                            ),
                            tint               = if (overdue) StatusOverdue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = dueStatusLabel(due, overdue, overdueDays),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overdue) StatusOverdue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Location
                todo.location?.takeIf { it.isNotBlank() }?.let { loc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Filled.LocationOn,
                            contentDescription = stringResource(R.string.label_location),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text     = loc,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

            }

            // Priority chip, right-aligned at the end of the row
            Spacer(Modifier.width(6.dp))
            PriorityChip(priority = todo.priority)
        }
    }
}

@Composable
private fun dueStatusLabel(
    due: LocalDate,
    overdue: Boolean,
    overdueDays: Long
): String {
    val date = due.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    )
    return if (overdue) {
        val days = pluralStringResource(
            R.plurals.overdue_days,
            overdueDays.toInt(),
            overdueDays.toInt()
        )
        stringResource(R.string.overdue_date_format, days, date)
    } else {
        stringResource(R.string.due_date_format, date)
    }
}
