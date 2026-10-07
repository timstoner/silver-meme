package com.tmstoner.silvermeme.data.storage

import com.tmstoner.silvermeme.data.model.VaultConfig
import kotlinx.coroutines.flow.Flow

/**
 * Persisted app settings.
 *
 * Multi-vault (Track G2): [gitRemoteUrl], [gitUsername], [gitToken] and [vaultPath]
 * (and their setters) read and write the ACTIVE vault, so single-vault callers keep
 * working unchanged. Author name/email, theme, filters and capacity are global.
 */
interface SettingsStore {
    // ── Vaults (Track G2) ─────────────────────────────────────────────────────
    /** All configured vaults; never empty (the pre-G2 setup becomes "Default"). */
    val vaults: Flow<List<VaultConfig>>
    /** Id of the vault the app reads and syncs. */
    val activeVaultId: Flow<String>
    /** Adds an empty, local-only vault with its own directory and returns it (not activated). */
    suspend fun addVault(name: String): VaultConfig
    suspend fun renameVault(id: String, name: String)
    /** Forgets the vault (its files stay on disk). Returns false if it is the last vault. */
    suspend fun removeVault(id: String): Boolean
    suspend fun setActiveVault(id: String)

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
