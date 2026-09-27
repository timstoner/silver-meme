package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.ui.theme.PriorityHigh
import com.tmstoner.silvermeme.ui.theme.PriorityLow
import com.tmstoner.silvermeme.ui.theme.PriorityMedium
import com.tmstoner.silvermeme.ui.theme.PriorityUrgent

/** A compact chip that displays the priority level with an appropriate colour. */
@Composable
fun PriorityChip(priority: Priority, modifier: Modifier = Modifier) {
    val (bgColor, label) = when (priority) {
        Priority.LOW    -> PriorityLow    to R.string.priority_low
        Priority.MEDIUM -> PriorityMedium to R.string.priority_medium
        Priority.HIGH   -> PriorityHigh   to R.string.priority_high
        Priority.URGENT -> PriorityUrgent to R.string.priority_urgent
    }
    Text(
        text     = stringResource(label),
        color    = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bgColor)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}
