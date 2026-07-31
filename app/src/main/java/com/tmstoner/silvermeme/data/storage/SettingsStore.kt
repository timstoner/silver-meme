package com.tmstoner.silvermeme.data.storage

import kotlinx.coroutines.flow.Flow

interface SettingsStore {
    val gitRemoteUrl: Flow<String>
    val gitUsername: Flow<String>
    val gitToken: Flow<String>
    val vaultPath: Flow<String>
    val authorName: Flow<String>
    val authorEmail: Flow<String>

    suspend fun setGitRemoteUrl(url: String)
    suspend fun setGitUsername(username: String)
    suspend fun setGitToken(token: String)
    suspend fun setVaultPath(path: String)
    suspend fun setAuthorName(name: String)
    suspend fun setAuthorEmail(email: String)
}
