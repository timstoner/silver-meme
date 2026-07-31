package com.tmstoner.silvermeme.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        private val KEY_THEME_MODE        = stringPreferencesKey("theme_mode")
        private val KEY_LAST_FILTER_STATE = stringPreferencesKey("last_filter_state")

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

    // ── Flows (reactive reads) ────────────────────────────────────────────────

    override val gitRemoteUrl: Flow<String> = context.dataStore.data
        .map { it[KEY_GIT_REMOTE_URL] ?: "" }

    override val gitUsername: Flow<String> = context.dataStore.data
        .map { it[KEY_GIT_USERNAME] ?: "" }

    /**
     * Cold flow that reads the PAT from [EncryptedSharedPreferences].
     *
     * Not reactive in the DataStore sense — callers use `.first()` inside suspend
     * functions, so a cold `flow { emit(...) }` is sufficient and avoids the
     * complexity of a SharedPreferences listener.
     */
    override val gitToken: Flow<String> = flow {
        emit(encryptedPrefs.getString(KEY_ENCRYPTED_TOKEN, "") ?: "")
    }.flowOn(Dispatchers.IO)

    override val vaultPath: Flow<String> = context.dataStore.data
        .map { it[KEY_VAULT_PATH] ?: "" }

    override val authorName: Flow<String> = context.dataStore.data
        .map { it[KEY_AUTHOR_NAME] ?: "SilverMeme" }

    override val authorEmail: Flow<String> = context.dataStore.data
        .map { it[KEY_AUTHOR_EMAIL] ?: "silvermeme@local" }

    /** "system" (default), "light", or "dark". */
    override val themeMode: Flow<String> = context.dataStore.data
        .map { it[KEY_THEME_MODE] ?: "system" }

    override val lastFilterState: Flow<String> = context.dataStore.data
        .map { it[KEY_LAST_FILTER_STATE] ?: "" }

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun setGitRemoteUrl(url: String) {
        context.dataStore.edit { it[KEY_GIT_REMOTE_URL] = url }
    }

    override suspend fun setGitUsername(username: String) {
        context.dataStore.edit { it[KEY_GIT_USERNAME] = username }
    }

    /**
     * Writes the PAT to [EncryptedSharedPreferences] only.
     * Also removes any residual plaintext value from DataStore.
     */
    override suspend fun setGitToken(token: String) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putString(KEY_ENCRYPTED_TOKEN, token).apply()
            // Ensure no plaintext copy lingers in DataStore
            context.dataStore.edit { it.remove(KEY_GIT_TOKEN) }
        }
    }

    override suspend fun setVaultPath(path: String) {
        context.dataStore.edit { it[KEY_VAULT_PATH] = path }
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
