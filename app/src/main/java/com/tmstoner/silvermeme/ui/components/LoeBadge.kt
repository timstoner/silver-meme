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
import com.tmstoner.silvermeme.ui.theme.LoeLarge
import com.tmstoner.silvermeme.ui.theme.LoeMedium
import com.tmstoner.silvermeme.ui.theme.LoeSmall

/**
 * A small badge showing the Level of Effort (LOE) estimate for a task.
 * Renders nothing when [loe] is 0 (not estimated).
 *
 * Color coding: 1-2 = quick (green), 3-5 = medium (amber), 8+ = large (red).
 */
@Composable
fun LoeBadge(loe: Int, modifier: Modifier = Modifier) {
    if (loe <= 0) return

    val color = when {
        loe <= 2 -> LoeSmall
        loe <= 5 -> LoeMedium
        else     -> LoeLarge
    }

    Text(
        text     = "LOE $loe",
        color    = Color.White,
        fontSize = 10.sp,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
