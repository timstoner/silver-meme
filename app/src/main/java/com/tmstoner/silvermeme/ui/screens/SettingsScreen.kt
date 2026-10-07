package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.ui.components.VaultSettingsSection
import com.tmstoner.silvermeme.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Settings screen for configuring the remote git repository.
 *
 * The user supplies:
 *  - Vaults (G2): which vault is active, plus add / rename / remove
 *  - Git remote HTTPS URL  (e.g. https://github.com/user/vault.git) — for the active vault
 *  - Username             (GitHub username)
 *  - Personal Access Token (kept locally in DataStore)
 *  - Author name / email  (for git commits)
 * Plus appearance (theme) and planning (daily effort capacity).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.settingsState.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val vaults by viewModel.vaults.collectAsState()
    val activeVaultId by viewModel.activeVaultId.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Editable copies of the settings
    // (keyed on the active vault too, so text typed for one vault is never saved into another)
    var remoteUrl    by remember(activeVaultId, state.gitRemoteUrl) { mutableStateOf(state.gitRemoteUrl) }
    var username     by remember(activeVaultId, state.gitUsername)  { mutableStateOf(state.gitUsername)  }
    var token        by remember(activeVaultId, state.gitToken)     { mutableStateOf(state.gitToken)     }
    var authorName   by remember(state.authorName)    { mutableStateOf(state.authorName)    }
    var authorEmail  by remember(state.authorEmail)   { mutableStateOf(state.authorEmail)   }
    var capacity     by remember(state.dailyCapacity) { mutableStateOf(state.dailyCapacity.toString()) }
    val capacityValid = SettingsViewModel.parseCapacity(capacity) != null
    var tokenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isSaved) {
        if (isSaved) {
            scope.launch { snackbarHostState.showSnackbar("Settings saved") }
            viewModel.clearSavedFlag()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (!capacityValid) {
                    scope.launch { snackbarHostState.showSnackbar("Daily capacity must be 0–${SettingsViewModel.MAX_DAILY_CAPACITY}") }
                    return@FloatingActionButton
                }
                viewModel.saveSettings(remoteUrl, username, token, authorName, authorEmail, capacity)
            }) {
                Icon(Icons.Filled.Done, contentDescription = "Save settings")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Section: Vaults (G2) ──────────────────────────────────────────
            VaultSettingsSection(
                vaults        = vaults,
                activeVaultId = activeVaultId,
                onSelect      = viewModel::setActiveVault,
                onAdd         = viewModel::addVault,
                onRename      = viewModel::renameVault,
                onRemove      = viewModel::removeVault
            )

            // ── Section: Git remote ───────────────────────────────────────────
            Text("Git Remote", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value         = remoteUrl,
                onValueChange = { remoteUrl = it },
                label         = { Text("Remote URL") },
                placeholder   = { Text("https://github.com/user/vault.git") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier      = Modifier.fillMaxWidth(),
                supportingText = { Text("HTTPS URL of your git repository") }
            )

            OutlinedTextField(
                value         = username,
                onValueChange = { username = it },
                label         = { Text("Username") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value                  = token,
                onValueChange          = { token = it },
                label                  = { Text("Personal Access Token") },
                singleLine             = true,
                visualTransformation   = if (tokenVisible) VisualTransformation.None
                                         else PasswordVisualTransformation(),
                keyboardOptions        = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon           = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            imageVector        = if (tokenVisible) Icons.Filled.VisibilityOff
                                                else Icons.Filled.Visibility,
                            contentDescription = if (tokenVisible) "Hide token" else "Show token"
                        )
                    }
                },
                modifier               = Modifier.fillMaxWidth(),
                supportingText         = { Text("GitHub PAT with repo scope") }
            )

            // Track G1: applied immediately, like the theme; the app schedules the worker.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value           = state.backgroundSync,
                        onValueChange   = viewModel::setBackgroundSync,
                        role            = Role.Switch
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Background sync", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Pull and push the active vault about once an hour, even when the app is closed. Needs a network connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = state.backgroundSync, onCheckedChange = null)
            }

            Spacer(Modifier.height(8.dp))

            // ── Section: Appearance ────────────────────────────────────────────
            Text("Appearance", style = MaterialTheme.typography.titleMedium)

            val themeOptions = listOf(
                "system" to "System default",
                "light"  to "Light",
                "dark"   to "Dark"
            )
            Column(Modifier.selectableGroup()) {
                themeOptions.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.themeMode == value,
                                onClick  = { viewModel.setThemeMode(value) },
                                role     = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.themeMode == value,
                            onClick  = { viewModel.setThemeMode(value) }
                        )
                        Spacer(Modifier.height(0.dp))
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Section: Planning ─────────────────────────────────────────────
            Text("Planning", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value           = capacity,
                onValueChange   = { input -> capacity = input.filter(Char::isDigit).take(3) },
                label           = { Text("Daily effort capacity") },
                suffix          = { Text("pts") },
                singleLine      = true,
                isError         = !capacityValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier        = Modifier.fillMaxWidth(),
                supportingText  = {
                    Text(
                        if (capacityValid) "Effort you plan per day, shown in the list's capacity bar. 0 hides the bar."
                        else "Enter 0–${SettingsViewModel.MAX_DAILY_CAPACITY}"
                    )
                }
            )

            Spacer(Modifier.height(8.dp))

            // ── Section: Git author ───────────────────────────────────────────
            Text("Git Author", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value         = authorName,
                onValueChange = { authorName = it },
                label         = { Text("Author name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value         = authorEmail,
                onValueChange = { authorEmail = it },
                label         = { Text("Author email") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier      = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // ── Vault path (read-only, informational) ─────────────────────────
            if (state.vaultPath.isNotBlank()) {
                Text("Vault location", style = MaterialTheme.typography.titleMedium)
                Text(
                    state.vaultPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(72.dp)) // Room for FAB
        }
    }
}
