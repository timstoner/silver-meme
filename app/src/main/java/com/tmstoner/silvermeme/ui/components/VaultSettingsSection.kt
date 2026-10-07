package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.data.model.VaultConfig
import com.tmstoner.silvermeme.data.model.VaultRegistry

/**
 * Settings section for multi-vault (Track G2): pick the active vault, add, rename
 * and remove vaults. The git fields below it in Settings edit the active vault.
 * Removing a vault only forgets it; its files stay on the device.
 */
@Composable
fun VaultSettingsSection(
    vaults: List<VaultConfig>,
    activeVaultId: String?,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var nameDialogFor by remember { mutableStateOf<VaultConfig?>(null) } // null id = new vault
    var showNameDialog by remember { mutableStateOf(false) }
    var removeCandidate by remember { mutableStateOf<VaultConfig?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text("Vaults", style = MaterialTheme.typography.titleMedium)
        Text(
            "The git settings below apply to the selected vault.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(Modifier.selectableGroup()) {
            vaults.forEach { vault ->
                val selected = vault.id == activeVaultId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, onClick = { onSelect(vault.id) }, role = Role.RadioButton)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { onSelect(vault.id) })
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(vault.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            vault.remoteUrl.ifBlank { "Local only" },
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { nameDialogFor = vault; showNameDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename ${vault.name}")
                    }
                    IconButton(onClick = { removeCandidate = vault }, enabled = vaults.size > 1) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove ${vault.name}")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = { nameDialogFor = null; showNameDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add vault", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (showNameDialog) {
        val editing = nameDialogFor
        var name by remember(editing?.id) {
            mutableStateOf(editing?.name ?: VaultRegistry.nextName(vaults))
        }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(if (editing == null) "Add vault" else "Rename vault") },
            text  = {
                Column {
                    OutlinedTextField(
                        value         = name,
                        onValueChange = { name = it },
                        label         = { Text("Name") },
                        singleLine    = true
                    )
                    if (editing == null) {
                        Text(
                            "Starts empty and local. Add a git remote below to clone into it.",
                            style    = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (editing == null) onAdd(name.trim()) else onRename(editing.id, name.trim())
                        showNameDialog = false
                    }
                ) { Text(if (editing == null) "Add" else "Rename") }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("Cancel") } }
        )
    }

    removeCandidate?.let { vault ->
        AlertDialog(
            onDismissRequest = { removeCandidate = null },
            title = { Text("Remove \"${vault.name}\"?") },
            text  = {
                Text(
                    "The app forgets this vault and its saved token. Its files stay on the device" +
                        (if (vault.path.isNotBlank()) " in ${vault.path}" else "") + "."
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemove(vault.id); removeCandidate = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removeCandidate = null }) { Text("Cancel") } }
        )
    }
}
