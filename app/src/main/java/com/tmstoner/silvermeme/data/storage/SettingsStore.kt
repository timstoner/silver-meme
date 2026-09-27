package com.tmstoner.silvermeme.data.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface SettingsStore {
    val gitRemoteUrl: Flow<String>
    val gitUsername: Flow<String>
    val gitToken: Flow<String>
    val vaultPath: Flow<String>
    val authorName: Flow<String>
    val authorEmail: Flow<String>
    /** Theme preference: "system", "light", or "dark". */
    val themeMode: Flow<String>
    /** Serialized [com.tmstoner.silvermeme.viewmodel.FilterState] snapshot (Track F). */
    val lastFilterState: Flow<String>
    /** Durable indication that local vault changes still need a successful remote sync. */
    val pendingSync: Flow<Boolean>

    suspend fun setGitRemoteUrl(url: String)
    suspend fun setGitUsername(username: String)
    suspend fun setGitToken(token: String)
    suspend fun setVaultPath(path: String)
    suspend fun setAuthorName(name: String)
    suspend fun setAuthorEmail(email: String)
    suspend fun setThemeMode(mode: String)
    suspend fun setLastFilterState(serialized: String)
    suspend fun setPendingSync(pending: Boolean)

    /**
     * Captures settings for one repository operation. Implementations may
     * override this to read backing storage atomically.
     */
    suspend fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        gitRemoteUrl = gitRemoteUrl.first(),
        gitUsername = gitUsername.first(),
        gitToken = gitToken.first(),
        vaultPath = vaultPath.first(),
        authorName = authorName.first(),
        authorEmail = authorEmail.first(),
        pendingSync = pendingSync.first()
    )
}

data class SettingsSnapshot(
    val gitRemoteUrl: String,
    val gitUsername: String,
    val gitToken: String,
    val vaultPath: String,
    val authorName: String,
    val authorEmail: String,
    val pendingSync: Boolean
) {
    val isGitConfigured: Boolean get() = gitRemoteUrl.isNotBlank()
}
