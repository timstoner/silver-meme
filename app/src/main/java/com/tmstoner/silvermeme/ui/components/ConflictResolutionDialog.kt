package com.tmstoner.silvermeme.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tmstoner.silvermeme.R

@Composable
fun ConflictResolutionDialog(
    conflictFiles: List<String>,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conflict_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.conflict_files_message,
                    conflictFiles.joinToString("\n") { "• $it" }
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onKeepRemote) {
                Text(stringResource(R.string.conflict_keep_remote))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepLocal) {
                Text(stringResource(R.string.conflict_keep_local))
            }
        }
    )
}
