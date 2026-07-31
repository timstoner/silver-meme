package com.tmstoner.silvermeme.data.storage

import kotlinx.coroutines.flow.Flow

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

    suspend fun setGitRemoteUrl(url: String)
    suspend fun setGitUsername(username: String)
    suspend fun setGitToken(token: String)
    suspend fun setVaultPath(path: String)
    suspend fun setAuthorName(name: String)
    suspend fun setAuthorEmail(email: String)
    suspend fun setThemeMode(mode: String)
    suspend fun setLastFilterState(serialized: String)
}
