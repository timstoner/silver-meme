package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Shows how much of the day's Level of Effort budget is taken by open tasks
 * due today, e.g. "Today · 8 / 21 pts". Turns to the error colour once the
 * total goes over [capacity].
 */
@Composable
fun CapacityBar(
    loe: Int,
    capacity: Int,
    modifier: Modifier = Modifier
) {
    val over     = capacity in 1 until loe
    val color    = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val progress = if (capacity <= 0) 0f else (loe.toFloat() / capacity).coerceAtMost(1f)
    val summary  = if (over) "$loe / $capacity pts · ${loe - capacity} over" else "$loe / $capacity pts"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Today's effort: $summary"
            }
    ) {
        Row {
            Text(
                "Today's effort",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(summary, style = MaterialTheme.typography.labelMedium, color = color)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress   = { progress },
            color      = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier   = Modifier.fillMaxWidth()
        )
    }
}
