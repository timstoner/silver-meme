package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.viewmodel.FilterState
import com.tmstoner.silvermeme.viewmodel.SortOrder

/**
 * Always-visible, horizontally scrolling row of list controls: sort, overdue,
 * priority, show-done, and a clear action when any chip filter is active.
 * Project filtering lives in the navigation drawer (phone) or project list (tablet).
 */
@Composable
fun FilterChipRow(
    filterState: FilterState,
    onSortOrder: (SortOrder) -> Unit,
    onToggleOverdue: () -> Unit,
    onPriority: (Priority) -> Unit,
    onToggleDone: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val hasChipFilters = filterState.priority != null || filterState.showCompleted || filterState.showOverdue

    Row(
        modifier              = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            AssistChip(
                onClick      = { showSortMenu = true },
                label        = { Text(filterState.sortOrder.label) },
                leadingIcon  = { Icon(Icons.Filled.SwapVert, contentDescription = null) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change sort order") }
            )
            DropdownMenu(
                expanded         = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text         = { Text(order.label) },
                        trailingIcon = if (order == filterState.sortOrder) {
                            { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                        } else null,
                        onClick      = {
                            onSortOrder(order)
                            showSortMenu = false
                        }
                    )
                }
            }
        }
        FilterChip(
            selected = filterState.showOverdue,
            onClick  = onToggleOverdue,
            label    = { Text("Overdue") }
        )
        // Most important first
        Priority.entries.sortedByDescending { it.sortOrder }.forEach { p ->
            FilterChip(
                selected = filterState.priority == p,
                onClick  = { onPriority(p) },
                label    = { Text(p.label.replaceFirstChar { it.uppercaseChar() }) }
            )
        }
        FilterChip(
            selected    = filterState.showCompleted,
            onClick     = onToggleDone,
            label       = { Text("Show done") },
            leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
        )
        if (hasChipFilters) {
            AssistChip(
                onClick     = onClear,
                label       = { Text("Clear") },
                leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) }
            )
        }
    }
}
