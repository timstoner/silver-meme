package com.tmstoner.silvermeme.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ConflictResolutionDialog(
    conflictFiles: List<String>,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync Conflict") },
        text = {
            Text(
                "The following files have conflicts:\n" +
                conflictFiles.joinToString("\n") { "• $it" } +
                "\n\nChoose which version to keep."
            )
        },
        confirmButton = {
            TextButton(onClick = onKeepRemote) {
                Text("Keep Remote")
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepLocal) {
                Text("Keep Local")
            }
        }
    )
}
