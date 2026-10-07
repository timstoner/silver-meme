package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import com.tmstoner.silvermeme.data.model.ChecklistItem

/** Saver so a checklist being edited survives rotation inside `rememberSaveable`. */
val ChecklistSaver = listSaver<List<ChecklistItem>, Any>(
    save    = { items -> items.flatMap { listOf(it.text, it.isDone) } },
    restore = { flat -> flat.chunked(2).map { (text, done) -> ChecklistItem(text as String, done as Boolean) } }
)

/**
 * Inline checklist editor for the task form: tick, edit and remove existing
 * items, and add new ones from the field at the bottom (Enter adds and keeps
 * the keyboard open for the next item).
 */
@Composable
fun ChecklistEditor(
    items: List<ChecklistItem>,
    onItemsChange: (List<ChecklistItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    var newItem by rememberSaveable { mutableStateOf("") }

    fun addItem() {
        val text = newItem.trim()
        if (text.isNotEmpty()) onItemsChange(items + ChecklistItem(text))
        newItem = ""
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Checklist", style = MaterialTheme.typography.labelLarge)
            if (items.isNotEmpty()) {
                Text(
                    "${items.count { it.isDone }}/${items.size} done",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked         = item.isDone,
                    onCheckedChange = { done ->
                        onItemsChange(items.toMutableList().also { it[index] = item.copy(isDone = done) })
                    }
                )
                BasicTextField(
                    value         = item.text,
                    onValueChange = { text ->
                        onItemsChange(items.toMutableList().also { it[index] = item.copy(text = text) })
                    },
                    singleLine    = true,
                    textStyle     = MaterialTheme.typography.bodyLarge.copy(
                        color          = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                                         else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else null
                    ),
                    cursorBrush   = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier      = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Checklist item ${index + 1}" }
                )
                IconButton(onClick = { onItemsChange(items.filterIndexed { i, _ -> i != index }) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove checklist item")
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value           = newItem,
                onValueChange   = { newItem = it },
                label           = { Text("Add item") },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                // Next (not Done) keeps the keyboard up for the next item.
                keyboardActions = KeyboardActions(onNext = { addItem() }),
                modifier        = Modifier.weight(1f)
            )
            IconButton(onClick = ::addItem, enabled = newItem.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = "Add checklist item")
            }
        }
    }
}
