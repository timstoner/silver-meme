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
    /**
     * Effort points the user plans to get through per day, shown against today's
     * total in the list's capacity bar. 0 hides the bar.
     */
    val dailyCapacity: Flow<Int>

    suspend fun setGitRemoteUrl(url: String)
    suspend fun setGitUsername(username: String)
    suspend fun setGitToken(token: String)
    suspend fun setVaultPath(path: String)
    suspend fun setAuthorName(name: String)
    suspend fun setAuthorEmail(email: String)
    suspend fun setThemeMode(mode: String)
    suspend fun setLastFilterState(serialized: String)
    suspend fun setDailyCapacity(points: Int)

    companion object {
        /** Daily effort capacity until the user sets their own; roughly an 8-hour work day. */
        const val DEFAULT_DAILY_CAPACITY = 21
    }
}
