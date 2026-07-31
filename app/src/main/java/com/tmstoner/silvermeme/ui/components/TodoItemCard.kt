package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.ui.theme.StatusDone
import com.tmstoner.silvermeme.ui.theme.StatusOverdue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * A card that summarises a single [TodoItem] with:
 *  - completion toggle
 *  - title (struck-through when done)
 *  - due date (red + "Nd overdue" suffix when overdue)
 *  - optional location, project, recurrence indicator
 *  - tags
 *  - priority chip and LOE badge, right-aligned at the end of the row
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick     = { 
                    if (onSelectionToggle != null) {
                        onSelectionToggle()
                    } else {
                        onClick(todo)
                    }
                },
                onLongClick = { onLongClick(todo) }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                overdue -> StatusOverdue.copy(alpha = 0.08f)
                todo.isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (overdue) BorderStroke(1.5.dp, StatusOverdue) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (todo.isCompleted) 0.dp else 2.dp)
    ) {
        Row(
            modifier        = Modifier.padding(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Completion toggle
            IconButton(
                onClick  = { onToggleComplete(todo) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector        = if (todo.isCompleted) Icons.Filled.CheckCircle
                                        else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (todo.isCompleted) "Mark incomplete" else "Mark complete",
                    tint               = if (todo.isCompleted) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // Title row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text           = todo.title,
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
                            contentDescription = "Repeats ${todo.recurrence}",
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Overdue banner
                if (overdue) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Filled.Warning,
                            contentDescription = "Overdue",
                            tint               = StatusOverdue,
                            modifier           = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text     = if (overdueDays == 1L) "1 day overdue" else "${overdueDays}d overdue",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = StatusOverdue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }

                // Due date
                todo.dueDate?.let { due ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Outlined.CalendarToday,
                            contentDescription = "Due date",
                            tint               = if (overdue) StatusOverdue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text  = due.format(DISPLAY_DATE_FORMATTER),
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
                            contentDescription = "Location",
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

                // Project tag
                todo.project.takeIf { it.isNotBlank() }?.let { project ->
                    Spacer(Modifier.height(2.dp))
                    ProjectTag(project = project)
                }

                // Tags
                if (todo.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        todo.tags.take(3).forEach { tag ->
                            Text(
                                text  = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (todo.tags.size > 3) {
                            Text(
                                text  = "+${todo.tags.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Priority chip + LOE badge, right-aligned at the end of the row
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                PriorityChip(priority = todo.priority)
                if (todo.loe > 0) {
                    Spacer(Modifier.height(4.dp))
                    LoeBadge(loe = todo.loe)
                }
            }
        }
    }
}
