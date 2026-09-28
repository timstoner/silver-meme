package com.tmstoner.silvermeme.data.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface SettingsStore {
    companion object {
        /** Default time after which a configured remote's sync state is stale. */
        const val DEFAULT_SYNC_STALE_THRESHOLD_MILLIS: Long = 24L * 60L * 60L * 1000L
    }

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
    /** Device-wide interval after which a remote sync is considered stale. */
    val syncStaleThresholdMillis: Flow<Long>
        get() = flowOf(DEFAULT_SYNC_STALE_THRESHOLD_MILLIS)
    /** Hour-based setting contract for settings consumers. */
    val dashboardStaleThresholdHours: Flow<Int>
        get() = syncStaleThresholdMillis.map { threshold ->
            (threshold / MILLIS_PER_HOUR).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

    /**
     * Serialized dashboard layout for one local vault. Dashboard preferences are
     * deliberately device-only and must not be written to the vault.
     */
    fun dashboardLayout(vaultPath: String): Flow<String> = flowOf("")
    /**
     * Last completed remote synchronization for this local vault, represented
     * as epoch milliseconds. Null means this device has not recorded one.
     */
    fun lastSuccessfulSyncTime(vaultPath: String): Flow<Long?> = flowOf(null)

    suspend fun setGitRemoteUrl(url: String)
    suspend fun setGitUsername(username: String)
    suspend fun setGitToken(token: String)
    suspend fun setVaultPath(path: String)
    suspend fun setAuthorName(name: String)
    suspend fun setAuthorEmail(email: String)
    suspend fun setThemeMode(mode: String)
    suspend fun setLastFilterState(serialized: String)
    suspend fun setPendingSync(pending: Boolean)
    suspend fun getSyncStaleThresholdMillis(): Long = syncStaleThresholdMillis.first()
    suspend fun setSyncStaleThresholdMillis(thresholdMillis: Long) = Unit
    suspend fun setDashboardStaleThresholdHours(hours: Int) {
        setSyncStaleThresholdMillis(hours.coerceAtLeast(1).toLong() * MILLIS_PER_HOUR)
    }
    suspend fun setDashboardLayout(vaultPath: String, serialized: String) = Unit
    /** Stores device-local sync status; it is never written into the vault. */
    suspend fun setLastSuccessfulSyncTime(vaultPath: String, epochMillis: Long) = Unit

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

private const val MILLIS_PER_HOUR: Long = 60L * 60L * 1000L

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
