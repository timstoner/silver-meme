package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.ui.theme.PriorityHigh
import com.tmstoner.silvermeme.ui.theme.PriorityLow
import com.tmstoner.silvermeme.ui.theme.PriorityMedium
import com.tmstoner.silvermeme.ui.theme.PriorityUrgent

/** A small chip that displays the priority level with an appropriate colour. */
@Composable
fun PriorityChip(priority: Priority, modifier: Modifier = Modifier) {
    val (bgColor, label) = when (priority) {
        Priority.LOW    -> PriorityLow    to "Low"
        Priority.MEDIUM -> PriorityMedium to "Medium"
        Priority.HIGH   -> PriorityHigh   to "High"
        Priority.URGENT -> PriorityUrgent to "Urgent"
    }
    Text(
        text     = label,
        color    = Color.White,
        fontSize = 11.sp,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
