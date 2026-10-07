package com.tmstoner.silvermeme.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tmstoner.silvermeme.data.model.VaultConfig
import com.tmstoner.silvermeme.data.storage.SettingsDataStore
import com.tmstoner.silvermeme.data.storage.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages UI state for the Settings screen.
 */
class SettingsViewModel(private val settingsDataStore: SettingsDataStore) : ViewModel() {

    // ── Exposed state ─────────────────────────────────────────────────────────

    val settingsState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsDataStore.gitRemoteUrl,
            settingsDataStore.gitUsername,
            settingsDataStore.gitToken
        ) { url, user, token -> Triple(url, user, token) },
        combine(
            settingsDataStore.vaultPath,
            settingsDataStore.authorName,
            settingsDataStore.authorEmail
        ) { path, name, email -> Triple(path, name, email) },
        combine(
            settingsDataStore.themeMode,
            settingsDataStore.dailyCapacity,
            settingsDataStore.backgroundSync
        ) { theme, capacity, backgroundSync -> Triple(theme, capacity, backgroundSync) }
    ) { (url, user, token), (path, name, email), (themeMode, dailyCapacity, backgroundSync) ->
        SettingsUiState(
            gitRemoteUrl = url,
            gitUsername  = user,
            gitToken     = token,
            vaultPath    = path,
            authorName   = name,
            authorEmail  = email,
            themeMode    = themeMode,
            dailyCapacity = dailyCapacity,
            backgroundSync = backgroundSync
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    // ── Vaults (Track G2) ─────────────────────────────────────────────────────

    val vaults: StateFlow<List<VaultConfig>> = settingsDataStore.vaults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeVaultId: StateFlow<String?> = settingsDataStore.activeVaultId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Adds a local vault and makes it active so its git settings can be filled in. */
    fun addVault(name: String) {
        viewModelScope.launch {
            val vault = settingsDataStore.addVault(name)
            settingsDataStore.setActiveVault(vault.id)
        }
    }

    fun renameVault(id: String, name: String) {
        viewModelScope.launch { settingsDataStore.renameVault(id, name) }
    }

    /** Forgets the vault; its files stay on the device. The last vault can't be removed. */
    fun removeVault(id: String) {
        viewModelScope.launch { settingsDataStore.removeVault(id) }
    }

    fun setActiveVault(id: String) {
        viewModelScope.launch { settingsDataStore.setActiveVault(id) }
    }

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveSettings(
        gitRemoteUrl: String,
        gitUsername: String,
        gitToken: String,
        authorName: String,
        authorEmail: String,
        dailyCapacity: String
    ) {
        viewModelScope.launch {
            parseCapacity(dailyCapacity)?.let { settingsDataStore.setDailyCapacity(it) }
            settingsDataStore.setGitRemoteUrl(gitRemoteUrl.trim())
            settingsDataStore.setGitUsername(gitUsername.trim())
            settingsDataStore.setGitToken(gitToken.trim())
            settingsDataStore.setAuthorName(authorName.trim())
            settingsDataStore.setAuthorEmail(authorEmail.trim())
            _isSaved.value = true
        }
    }

    /** Updates the theme preference immediately (applied without needing to press Save). */
    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }

    /**
     * Turns periodic background sync on or off immediately. `SilverMemeApplication`
     * watches the setting and schedules or cancels the worker.
     */
    fun setBackgroundSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setBackgroundSync(enabled)
        }
    }

    fun clearSavedFlag() {
        _isSaved.value = false
    }

    companion object {
        const val MAX_DAILY_CAPACITY = 200

        /**
         * Reads the capacity field: whole points in 0..[MAX_DAILY_CAPACITY], where 0 turns
         * the capacity bar off. Blank means "back to the default". Anything else is
         * rejected (null) so a typo never overwrites the saved value.
         */
        fun parseCapacity(input: String): Int? {
            val text = input.trim()
            if (text.isEmpty()) return SettingsStore.DEFAULT_DAILY_CAPACITY
            return text.toIntOrNull()?.takeIf { it in 0..MAX_DAILY_CAPACITY }
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val store: SettingsDataStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(store) as T
    }
}

data class SettingsUiState(
    val gitRemoteUrl: String = "",
    val gitUsername:  String = "",
    val gitToken:     String = "",
    val vaultPath:    String = "",
    val authorName:   String = "SilverMeme",
    val authorEmail:  String = "silvermeme@local",
    /** "system", "light", or "dark". */
    val themeMode:    String = "system",
    /** Effort points per day for the capacity bar; 0 turns it off. */
    val dailyCapacity: Int   = SettingsStore.DEFAULT_DAILY_CAPACITY,
    /** Periodic sync of the active vault while the app is closed (Track G1). */
    val backgroundSync: Boolean = false
)
