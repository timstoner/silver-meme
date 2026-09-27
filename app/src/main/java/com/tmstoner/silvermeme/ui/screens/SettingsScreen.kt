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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tmstoner.silvermeme.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Settings screen for configuring the remote git repository.
 *
 * The user supplies:
 *  - Git remote HTTPS URL  (e.g. https://github.com/user/vault.git)
 *  - Username             (GitHub username)
 *  - Personal Access Token (kept locally in DataStore)
 *  - Author name / email  (for git commits)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenNavigationDrawer: () -> Unit
) {
    val state by viewModel.settingsState.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Editable copies of the settings
    var remoteUrl    by remember(state.gitRemoteUrl)  { mutableStateOf(state.gitRemoteUrl)  }
    var username     by remember(state.gitUsername)   { mutableStateOf(state.gitUsername)   }
    var token        by remember(state.gitToken)      { mutableStateOf(state.gitToken)      }
    var authorName   by remember(state.authorName)    { mutableStateOf(state.authorName)    }
    var authorEmail  by remember(state.authorEmail)   { mutableStateOf(state.authorEmail)   }
    var tokenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isSaved) {
        if (isSaved) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_saved)) }
            viewModel.clearSavedFlag()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onOpenNavigationDrawer) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.cd_open_navigation)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.saveSettings(remoteUrl, username, token, authorName, authorEmail)
            }) {
                Icon(Icons.Filled.Done, contentDescription = stringResource(R.string.settings_save_action))
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
            // ── Section: Git remote ───────────────────────────────────────────
            Text(stringResource(R.string.settings_git_remote), style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value         = remoteUrl,
                onValueChange = { remoteUrl = it },
                label         = { Text(stringResource(R.string.settings_git_url)) },
                placeholder   = { Text(stringResource(R.string.settings_remote_example)) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier      = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.settings_https_hint)) }
            )

            OutlinedTextField(
                value         = username,
                onValueChange = { username = it },
                label         = { Text(stringResource(R.string.settings_git_username)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value                  = token,
                onValueChange          = { token = it },
                label                  = { Text(stringResource(R.string.settings_git_token)) },
                singleLine             = true,
                visualTransformation   = if (tokenVisible) VisualTransformation.None
                                         else PasswordVisualTransformation(),
                keyboardOptions        = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon           = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            imageVector        = if (tokenVisible) Icons.Filled.VisibilityOff
                                                else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (tokenVisible) R.string.settings_token_hide else R.string.settings_token_show
                            )
                        )
                    }
                },
                modifier               = Modifier.fillMaxWidth(),
                supportingText         = { Text(stringResource(R.string.settings_token_scope_hint)) }
            )

            Spacer(Modifier.height(8.dp))

            // ── Section: Appearance ────────────────────────────────────────────
            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)

            val themeOptions = listOf(
                "system" to R.string.settings_theme_system,
                "light"  to R.string.settings_theme_light,
                "dark"   to R.string.settings_theme_dark
            )
            Column(Modifier.selectableGroup()) {
                themeOptions.forEach { (value, labelRes) ->
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
                        Text(stringResource(labelRes), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Section: Git author ───────────────────────────────────────────
            Text(stringResource(R.string.settings_git_author), style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value         = authorName,
                onValueChange = { authorName = it },
                label         = { Text(stringResource(R.string.settings_author_name)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value         = authorEmail,
                onValueChange = { authorEmail = it },
                label         = { Text(stringResource(R.string.settings_author_email)) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier      = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // ── Vault path (read-only, informational) ─────────────────────────
            if (state.vaultPath.isNotBlank()) {
                Text(stringResource(R.string.settings_vault_location), style = MaterialTheme.typography.titleMedium)
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
