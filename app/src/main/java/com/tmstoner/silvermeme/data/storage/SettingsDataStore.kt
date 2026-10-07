package com.tmstoner.silvermeme.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tmstoner.silvermeme.data.model.VaultCodec
import com.tmstoner.silvermeme.data.model.VaultConfig
import com.tmstoner.silvermeme.data.model.VaultRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "silver_meme_settings")

/**
 * Persists user settings using Jetpack DataStore.
 *
 * The git PAT (personal access token) is stored in [EncryptedSharedPreferences]
 * backed by AES-256-GCM so it never lands in plaintext on disk. All other settings
 * (URLs, names, paths) remain in DataStore — they are not secrets.
 *
 * On first run after upgrading from a build that stored the token in DataStore,
 * [migrateToken] copies the plaintext value to the encrypted store and removes it.
 */
class SettingsDataStore(private val context: Context) : SettingsStore {

    companion object {
        // DataStore keys — KEY_GIT_TOKEN is kept only for migration reads; writes
        // go to EncryptedSharedPreferences exclusively after the migration.
        private val KEY_GIT_REMOTE_URL    = stringPreferencesKey("git_remote_url")
        private val KEY_GIT_USERNAME      = stringPreferencesKey("git_username")
        internal val KEY_GIT_TOKEN        = stringPreferencesKey("git_token")
        private val KEY_VAULT_PATH        = stringPreferencesKey("vault_path")
        private val KEY_AUTHOR_NAME       = stringPreferencesKey("author_name")
        private val KEY_AUTHOR_EMAIL      = stringPreferencesKey("author_email")
        // Multi-vault (G2). The single-vault keys above (remote URL, username, vault
        // path) are kept in sync with the "default" vault so a downgrade still works.
        private val KEY_VAULTS            = stringPreferencesKey("vaults")
        private val KEY_ACTIVE_VAULT_ID   = stringPreferencesKey("active_vault_id")
        private val KEY_THEME_MODE        = stringPreferencesKey("theme_mode")
        private val KEY_LAST_FILTER_STATE = stringPreferencesKey("last_filter_state")
        private val KEY_DAILY_CAPACITY    = intPreferencesKey("daily_capacity")

        // EncryptedSharedPreferences key for the PAT
        private const val ENCRYPTED_PREFS_FILE = "secure_settings"
        private const val KEY_ENCRYPTED_TOKEN  = "git_token"
    }

    // ── Encrypted storage for the PAT ─────────────────────────────────────────

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: android.content.SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    init {
        // Migrate any pre-existing plaintext token from DataStore to EncryptedSharedPreferences.
        // Runs asynchronously; token reads that happen before migration completes will correctly
        // fall through to the encrypted store (which may return empty on first launch — acceptable).
        CoroutineScope(Dispatchers.IO).launch {
            migrateToken()
        }
    }

    /**
     * One-time migration: if DataStore holds a plaintext PAT and the encrypted store is empty,
     * copy the token then erase the plaintext copy.
     */
    private suspend fun migrateToken() {
        val plaintext = context.dataStore.data.first()[KEY_GIT_TOKEN]
        if (!plaintext.isNullOrEmpty() &&
            encryptedPrefs.getString(KEY_ENCRYPTED_TOKEN, "").isNullOrEmpty()
        ) {
            encryptedPrefs.edit().putString(KEY_ENCRYPTED_TOKEN, plaintext).apply()
            context.dataStore.edit { it.remove(KEY_GIT_TOKEN) }
            android.util.Log.i("SettingsDataStore", "PAT migrated from DataStore to EncryptedSharedPreferences")
        }
    }

    // ── Vaults (Track G2) ─────────────────────────────────────────────────────

    private fun vaultsIn(prefs: Preferences): List<VaultConfig> =
        VaultRegistry.resolve(
            serialized      = prefs[KEY_VAULTS],
            legacyRemoteUrl = prefs[KEY_GIT_REMOTE_URL] ?: "",
            legacyUsername  = prefs[KEY_GIT_USERNAME] ?: "",
            legacyPath      = prefs[KEY_VAULT_PATH] ?: ""
        )

    private fun activeIn(prefs: Preferences): VaultConfig =
        VaultRegistry.activeOf(vaultsIn(prefs), prefs[KEY_ACTIVE_VAULT_ID])

    /** Writes [vaults] and mirrors the default vault into the single-vault keys. */
    private fun MutablePreferences.storeVaults(vaults: List<VaultConfig>) {
        this[KEY_VAULTS] = VaultCodec.encode(vaults)
        vaults.firstOrNull { it.id == VaultRegistry.DEFAULT_ID }?.let { default ->
            this[KEY_GIT_REMOTE_URL] = default.remoteUrl
            this[KEY_GIT_USERNAME]   = default.username
            this[KEY_VAULT_PATH]     = default.path
        }
    }

    private suspend fun editActiveVault(transform: (VaultConfig) -> VaultConfig) {
        context.dataStore.edit { prefs ->
            val active = activeIn(prefs)
            prefs.storeVaults(VaultRegistry.update(vaultsIn(prefs), active.id, transform))
        }
    }

    override val vaults: Flow<List<VaultConfig>> = context.dataStore.data
        .map { vaultsIn(it) }
        .distinctUntilChanged()

    override val activeVaultId: Flow<String> = context.dataStore.data
        .map { activeIn(it).id }
        .distinctUntilChanged()

    override suspend fun addVault(name: String): VaultConfig {
        val id = UUID.randomUUID().toString().take(8)
        val dir = context.getExternalFilesDir("vaults/$id") ?: File(context.filesDir, "vaults/$id")
        val vault = VaultConfig(id = id, name = name.trim().ifBlank { "Vault" }, path = dir.absolutePath)
        context.dataStore.edit { prefs -> prefs.storeVaults(vaultsIn(prefs) + vault) }
        return vault
    }

    override suspend fun renameVault(id: String, name: String) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs.storeVaults(VaultRegistry.update(vaultsIn(prefs), id) { it.copy(name = cleaned) })
        }
    }

    override suspend fun removeVault(id: String): Boolean {
        var removed = false
        context.dataStore.edit { prefs ->
            val remaining = VaultRegistry.remove(vaultsIn(prefs), id) ?: return@edit
            prefs.storeVaults(remaining)
            if (prefs[KEY_ACTIVE_VAULT_ID] == id) prefs[KEY_ACTIVE_VAULT_ID] = remaining.first().id
            removed = true
        }
        if (removed) {
            withContext(Dispatchers.IO) {
                encryptedPrefs.edit().remove(VaultRegistry.tokenKey(id)).apply()
            }
        }
        return removed
    }

    override suspend fun setActiveVault(id: String) {
        context.dataStore.edit { prefs ->
            if (vaultsIn(prefs).any { it.id == id }) prefs[KEY_ACTIVE_VAULT_ID] = id
        }
    }

    // ── Flows (reactive reads) ────────────────────────────────────────────────

    override val gitRemoteUrl: Flow<String> = context.dataStore.data
        .map { activeIn(it).remoteUrl }

    override val gitUsername: Flow<String> = context.dataStore.data
        .map { activeIn(it).username }

    /**
     * The active vault's PAT from [EncryptedSharedPreferences].
     *
     * Re-emits when the active vault changes, not when the token itself is
     * rewritten — callers use `.first()` inside suspend functions, so that is
     * sufficient and avoids a SharedPreferences listener.
     */
    override val gitToken: Flow<String> = activeVaultId
        .map { id -> encryptedPrefs.getString(VaultRegistry.tokenKey(id), "") ?: "" }
        .flowOn(Dispatchers.IO)

    override val vaultPath: Flow<String> = context.dataStore.data
        .map { activeIn(it).path }

    override val authorName: Flow<String> = context.dataStore.data
        .map { it[KEY_AUTHOR_NAME] ?: "SilverMeme" }

    override val authorEmail: Flow<String> = context.dataStore.data
        .map { it[KEY_AUTHOR_EMAIL] ?: "silvermeme@local" }

    /** "system" (default), "light", or "dark". */
    override val themeMode: Flow<String> = context.dataStore.data
        .map { it[KEY_THEME_MODE] ?: "system" }

    override val lastFilterState: Flow<String> = context.dataStore.data
        .map { it[KEY_LAST_FILTER_STATE] ?: "" }

    override val dailyCapacity: Flow<Int> = context.dataStore.data
        .map { it[KEY_DAILY_CAPACITY] ?: SettingsStore.DEFAULT_DAILY_CAPACITY }

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun setGitRemoteUrl(url: String) {
        editActiveVault { it.copy(remoteUrl = url) }
    }

    override suspend fun setGitUsername(username: String) {
        editActiveVault { it.copy(username = username) }
    }

    /**
     * Writes the active vault's PAT to [EncryptedSharedPreferences] only.
     * Also removes any residual plaintext value from DataStore.
     */
    override suspend fun setGitToken(token: String) {
        val key = VaultRegistry.tokenKey(activeVaultId.first())
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putString(key, token).apply()
            // Ensure no plaintext copy lingers in DataStore
            context.dataStore.edit { it.remove(KEY_GIT_TOKEN) }
        }
    }

    override suspend fun setVaultPath(path: String) {
        editActiveVault { it.copy(path = path) }
    }

    override suspend fun setAuthorName(name: String) {
        context.dataStore.edit { it[KEY_AUTHOR_NAME] = name }
    }

    override suspend fun setAuthorEmail(email: String) {
        context.dataStore.edit { it[KEY_AUTHOR_EMAIL] = email }
    }

    override suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    override suspend fun setLastFilterState(serialized: String) {
        context.dataStore.edit { it[KEY_LAST_FILTER_STATE] = serialized }
    }

    override suspend fun setDailyCapacity(points: Int) {
        context.dataStore.edit { it[KEY_DAILY_CAPACITY] = points }
    }

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
