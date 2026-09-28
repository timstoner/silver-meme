package com.tmstoner.silvermeme.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tmstoner.silvermeme.data.storage.SettingsDataStore
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
        settingsDataStore.themeMode,
        settingsDataStore.dashboardStaleThresholdHours
    ) { (url, user, token), (path, name, email), themeMode, staleThresholdHours ->
        SettingsUiState(
            gitRemoteUrl = url,
            gitUsername  = user,
            gitToken     = token,
            vaultPath    = path,
            authorName   = name,
            authorEmail  = email,
            themeMode    = themeMode,
            dashboardStaleThresholdHours = staleThresholdHours
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveSettings(
        gitRemoteUrl: String,
        gitUsername: String,
        gitToken: String,
        authorName: String,
        authorEmail: String
    ) {
        viewModelScope.launch {
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

    /** Updates the duration after which dashboard sync information is called stale. */
    fun setDashboardStaleThresholdHours(hours: Int) {
        viewModelScope.launch {
            settingsDataStore.setDashboardStaleThresholdHours(hours)
        }
    }

    fun clearSavedFlag() {
        _isSaved.value = false
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
    val dashboardStaleThresholdHours: Int = 24
)
