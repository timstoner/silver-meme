package com.tmstoner.silvermeme.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "silver_meme_settings")

/**
 * Persists user settings using Jetpack DataStore.
 *
 * Settings include the git remote configuration needed to clone/pull/push the
 * vault repository, plus the local path where the vault is stored on device.
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_GIT_REMOTE_URL = stringPreferencesKey("git_remote_url")
        private val KEY_GIT_USERNAME   = stringPreferencesKey("git_username")
        private val KEY_GIT_TOKEN      = stringPreferencesKey("git_token")
        private val KEY_VAULT_PATH     = stringPreferencesKey("vault_path")
        private val KEY_AUTHOR_NAME    = stringPreferencesKey("author_name")
        private val KEY_AUTHOR_EMAIL   = stringPreferencesKey("author_email")
    }

    // ── Flows (reactive reads) ────────────────────────────────────────────────

    val gitRemoteUrl: Flow<String> = context.dataStore.data
        .map { it[KEY_GIT_REMOTE_URL] ?: "" }

    val gitUsername: Flow<String> = context.dataStore.data
        .map { it[KEY_GIT_USERNAME] ?: "" }

    val gitToken: Flow<String> = context.dataStore.data
        .map { it[KEY_GIT_TOKEN] ?: "" }

    val vaultPath: Flow<String> = context.dataStore.data
        .map { it[KEY_VAULT_PATH] ?: "" }

    val authorName: Flow<String> = context.dataStore.data
        .map { it[KEY_AUTHOR_NAME] ?: "SilverMeme" }

    val authorEmail: Flow<String> = context.dataStore.data
        .map { it[KEY_AUTHOR_EMAIL] ?: "silvermeme@local" }

    // ── Writes ────────────────────────────────────────────────────────────────

    suspend fun setGitRemoteUrl(url: String) =
        context.dataStore.edit { it[KEY_GIT_REMOTE_URL] = url }

    suspend fun setGitUsername(username: String) =
        context.dataStore.edit { it[KEY_GIT_USERNAME] = username }

    suspend fun setGitToken(token: String) =
        context.dataStore.edit { it[KEY_GIT_TOKEN] = token }

    suspend fun setVaultPath(path: String) =
        context.dataStore.edit { it[KEY_VAULT_PATH] = path }

    suspend fun setAuthorName(name: String) =
        context.dataStore.edit { it[KEY_AUTHOR_NAME] = name }

    suspend fun setAuthorEmail(email: String) =
        context.dataStore.edit { it[KEY_AUTHOR_EMAIL] = email }

    /** Returns a snapshot of all settings (non-reactive, for one-shot reads). */
    data class Snapshot(
        val gitRemoteUrl: String,
        val gitUsername: String,
        val gitToken: String,
        val vaultPath: String,
        val authorName: String,
        val authorEmail: String
    ) {
        val isConfigured: Boolean get() = gitRemoteUrl.isNotBlank()
    }
}
