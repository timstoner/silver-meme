package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.util.QuickAddParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val PREVIEW_DATE = DateTimeFormatter.ofPattern("EEE, MMM d")

/**
 * One-line task entry pinned to the bottom of the list. Understands
 * `Buy milk tomorrow !high #errand` (see [QuickAddParser]) and shows what it
 * parsed under the field before the task is added.
 */
@Composable
fun QuickAddBar(
    onAdd: (QuickAddParser.Result) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { QuickAddParser.parse(text) }

    fun submit() {
        if (!parsed.isValid) return
        onAdd(parsed)
        text = ""
    }

    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value           = text,
                onValueChange   = { text = it },
                placeholder     = { Text("Quick add: Buy milk tomorrow !high #errand") },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                trailingIcon    = {
                    IconButton(onClick = ::submit, enabled = parsed.isValid) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add task")
                    }
                },
                modifier        = Modifier.fillMaxWidth()
            )
            previewText(parsed)?.let { preview ->
                Text(
                    text     = preview,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
        }
    }
}

/** e.g. "Due Wed, Oct 7 · High · #errand", or null when nothing beyond the title was parsed. */
private fun previewText(parsed: QuickAddParser.Result): String? {
    val today = LocalDate.now()
    val parts = buildList {
        parsed.dueDate?.let { due ->
            add(
                when (due) {
                    today              -> "Due today"
                    today.plusDays(1)  -> "Due tomorrow"
                    else               -> "Due ${due.format(PREVIEW_DATE)}"
                }
            )
        }
        parsed.priority?.let { add(it.label.replaceFirstChar { c -> c.uppercaseChar() }) }
        parsed.tags.forEach { add("#$it") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
