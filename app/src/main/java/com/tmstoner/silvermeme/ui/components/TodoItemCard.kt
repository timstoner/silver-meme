package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.ui.theme.StatusDone
import com.tmstoner.silvermeme.ui.theme.StatusOverdue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE_THIS_YEAR  = DateTimeFormatter.ofPattern("MMM d")
private val DATE_OTHER_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * A card that summarises a single [TodoItem] with:
 *  - completion toggle
 *  - title (struck-through when done)
 *  - one wrapping metadata line: due date (red, with "Nd overdue" when late),
 *    location, project, and up to 3 tags
 *  - recurrence indicator next to the title
 *  - priority chip (High/Urgent only — Low/Medium are the quiet default) and
 *    LOE badge, right-aligned at the end of the row
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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

                val hasMetadata = todo.dueDate != null || !todo.location.isNullOrBlank() ||
                    todo.project.isNotBlank() || todo.tags.isNotEmpty()
                if (hasMetadata) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(2.dp)
                    ) {
                        // Due date, with overdue count folded into the same label
                        todo.dueDate?.let { due ->
                            val color = if (overdue) StatusOverdue else MaterialTheme.colorScheme.onSurfaceVariant
                            val dateText = due.format(if (due.year == today.year) DATE_THIS_YEAR else DATE_OTHER_YEAR)
                            MetaItem(
                                icon       = Icons.Outlined.CalendarToday,
                                iconLabel  = if (overdue) "Overdue, due" else "Due",
                                text       = if (overdue) "$dateText · ${overdueDays}d overdue" else dateText,
                                color      = color,
                                bold       = overdue
                            )
                        }
                        todo.location?.takeIf { it.isNotBlank() }?.let { loc ->
                            MetaItem(
                                icon      = Icons.Filled.LocationOn,
                                iconLabel = "Location",
                                text      = loc,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        todo.project.takeIf { it.isNotBlank() }?.let { project ->
                            ProjectTag(project = project)
                        }
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
            val showPriority = todo.priority == Priority.HIGH || todo.priority == Priority.URGENT
            if (showPriority || todo.loe > 0) {
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (showPriority) PriorityChip(priority = todo.priority)
                    if (todo.loe > 0) {
                        if (showPriority) Spacer(Modifier.height(4.dp))
                        LoeBadge(loe = todo.loe)
                    }
                }
            }
        }
    }
}

/** One icon + label entry in the card's metadata line. */
@Composable
private fun MetaItem(
    icon: ImageVector,
    iconLabel: String,
    text: String,
    color: Color,
    bold: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector        = icon,
            contentDescription = iconLabel,
            tint               = color,
            modifier           = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text       = text,
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = if (bold) FontWeight.Bold else null,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
    }
}
