package com.tmstoner.silvermeme.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.data.model.NoteIndex
import com.tmstoner.silvermeme.util.WikiLinks

/**
 * The task form's markdown notes field with Obsidian wikilink support (Track G5):
 *  - typing `[[` lists matching vault notes above the field; tapping one inserts
 *    `[[Note]]`
 *  - links already in the notes show as chips that open the note in Obsidian
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WikiLinkNotesField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    noteIndex: NoteIndex,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cursor = value.selection.end
    val openLink = if (value.selection.collapsed) WikiLinks.openLinkAt(value.text, cursor) else null
    val suggestions = remember(openLink, noteIndex) {
        openLink?.let { WikiLinks.suggest(it.query, noteIndex.noteNames) }.orEmpty()
    }
    val linkTargets = remember(value.text) { WikiLinks.targets(value.text) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Suggestions sit above the field: the form pans rather than resizes for the
        // keyboard, so anything below the field would be hidden behind it.
        if (openLink != null && suggestions.isNotEmpty()) {
            Surface(
                tonalElevation = 3.dp,
                shape          = MaterialTheme.shapes.small,
                modifier       = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {
                Column {
                    suggestions.forEachIndexed { index, name ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val (text, newCursor) = WikiLinks.complete(value.text, cursor, openLink, name)
                                    onValueChange(TextFieldValue(text, TextRange(newCursor)))
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(18.dp)
                            )
                            Text(
                                name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            label         = { Text("Notes (Markdown)") },
            supportingText = if (openLink == null && linkTargets.isEmpty()) {
                { Text("Type [[ to link a note") }
            } else null,
            minLines      = 4,
            modifier      = Modifier.fillMaxWidth()
        )

        if (linkTargets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier              = Modifier.padding(top = 4.dp)
            ) {
                linkTargets.forEach { target ->
                    AssistChip(
                        onClick     = {
                            val uri = Uri.parse(WikiLinks.obsidianUri(noteIndex.vaultName, target))
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "Obsidian isn't installed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        label       = { Text(target, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = "Open in Obsidian") }
                    )
                }
            }
        }
    }
}
