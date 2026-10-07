package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.data.model.VaultConfig

/**
 * Shows the active vault and lets the user switch to another one (Track G2).
 * Renders nothing when there is only one vault, so single-vault users see no change.
 * Vaults are added, renamed and removed in Settings.
 */
@Composable
fun VaultSwitcher(
    vaults: List<VaultConfig>,
    activeVaultId: String?,
    onSwitch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (vaults.size < 2) return
    val active = vaults.firstOrNull { it.id == activeVaultId } ?: vaults.first()
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        NavigationDrawerItem(
            label    = { Text(active.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            icon     = { Icon(Icons.Filled.Storage, contentDescription = null) },
            badge    = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch vault") },
            selected = false,
            onClick  = { expanded = true },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            vaults.forEach { vault ->
                DropdownMenuItem(
                    text         = { Text(vault.name) },
                    leadingIcon  = { Icon(Icons.Filled.Storage, contentDescription = null) },
                    trailingIcon = if (vault.id == active.id) {
                        { Icon(Icons.Filled.Check, contentDescription = "Active vault") }
                    } else null,
                    onClick      = {
                        expanded = false
                        if (vault.id != active.id) onSwitch(vault.id)
                    }
                )
            }
        }
    }
}
