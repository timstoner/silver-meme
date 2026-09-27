package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.tmstoner.silvermeme.R
import androidx.compose.ui.unit.dp

/**
 * A small tag showing which project/folder a task belongs to (Track E3/F).
 * Tapping invokes [onClick] when provided, e.g. to filter the list by this project.
 */
@Composable
fun ProjectTag(project: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (project.isBlank()) return

    var rowModifier = modifier
        .clip(RoundedCornerShape(4.dp))
    if (onClick != null) {
        rowModifier = rowModifier.clickable { onClick() }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier.padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector        = Icons.Filled.Folder,
            contentDescription = stringResource(R.string.project_tag_description),
            tint               = MaterialTheme.colorScheme.secondary,
            modifier           = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text     = project,
            style = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.secondary
        )
    }
}
