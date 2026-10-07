package com.tmstoner.silvermeme.data.model

/**
 * One git-backed vault the app can switch between (Track G2).
 *
 * Only non-secret fields live here; the PAT for each vault is kept in
 * EncryptedSharedPreferences under [VaultRegistry.tokenKey].
 *
 * @param path absolute local directory, or blank for the default vault until the
 * repository first resolves it (falls back to `getExternalFilesDir("vault")`).
 */
data class VaultConfig(
    val id: String,
    val name: String,
    val path: String = "",
    val remoteUrl: String = "",
    val username: String = ""
)

/**
 * Pure helpers for the vault list: migration from the single-vault settings,
 * active-vault lookup, and list edits. Kept free of Android so it is unit-testable.
 */
object VaultRegistry {

    /** Id of the vault migrated from the pre-G2 single-vault settings. */
    const val DEFAULT_ID = "default"
    const val DEFAULT_NAME = "Default"

    /**
     * Returns the vault list. When nothing has been saved yet ([serialized] null or
     * blank) the pre-G2 single-vault settings become the one "Default" vault, so
     * existing users see exactly the vault they had.
     */
    fun resolve(
        serialized: String?,
        legacyRemoteUrl: String,
        legacyUsername: String,
        legacyPath: String
    ): List<VaultConfig> {
        val decoded = if (serialized.isNullOrBlank()) emptyList() else VaultCodec.decode(serialized)
        return decoded.ifEmpty {
            listOf(
                VaultConfig(
                    id        = DEFAULT_ID,
                    name      = DEFAULT_NAME,
                    path      = legacyPath,
                    remoteUrl = legacyRemoteUrl,
                    username  = legacyUsername
                )
            )
        }
    }

    /** The vault with [activeId], or the first vault if that id is unknown or unset. */
    fun activeOf(vaults: List<VaultConfig>, activeId: String?): VaultConfig =
        vaults.firstOrNull { it.id == activeId } ?: vaults.first()

    /** Replaces the vault with [id] by [transform] of it; other vaults are untouched. */
    fun update(vaults: List<VaultConfig>, id: String, transform: (VaultConfig) -> VaultConfig): List<VaultConfig> =
        vaults.map { if (it.id == id) transform(it) else it }

    /**
     * Removes the vault with [id]. Returns null (no change) when it is the only vault,
     * because the app always needs one.
     */
    fun remove(vaults: List<VaultConfig>, id: String): List<VaultConfig>? {
        if (vaults.size <= 1 || vaults.none { it.id == id }) return null
        return vaults.filterNot { it.id == id }
    }

    /**
     * EncryptedSharedPreferences key for a vault's PAT. The default vault keeps the
     * pre-G2 key so the token saved before the upgrade is still found.
     */
    fun tokenKey(vaultId: String): String =
        if (vaultId == DEFAULT_ID) "git_token" else "git_token_$vaultId"

    /** A display name not already used, e.g. "Vault 2". */
    fun nextName(vaults: List<VaultConfig>): String {
        val taken = vaults.map { it.name }.toSet()
        return generateSequence(2) { it + 1 }.map { "Vault $it" }.first { it !in taken }
    }
}

/**
 * Text format for the vault list in DataStore: one vault per line, fields
 * separated by tabs, with `\`, tab and newline escaped. Malformed lines are skipped.
 */
object VaultCodec {

    private const val FIELD_COUNT = 5

    fun encode(vaults: List<VaultConfig>): String =
        vaults.joinToString("\n") { v ->
            listOf(v.id, v.name, v.path, v.remoteUrl, v.username).joinToString("\t") { escape(it) }
        }

    fun decode(text: String): List<VaultConfig> =
        text.split('\n').mapNotNull { line ->
            val fields = splitFields(line)
            if (fields.size != FIELD_COUNT || fields[0].isBlank()) return@mapNotNull null
            VaultConfig(
                id        = fields[0],
                name      = fields[1],
                path      = fields[2],
                remoteUrl = fields[3],
                username  = fields[4]
            )
        }.distinctBy { it.id }

    private fun escape(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    /** Splits on unescaped tabs and unescapes each field. */
    private fun splitFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\\' && i + 1 < line.length -> {
                    when (val next = line[i + 1]) {
                        't'  -> current.append('\t')
                        'n'  -> current.append('\n')
                        'r'  -> current.append('\r')
                        else -> current.append(next)
                    }
                    i++
                }
                c == '\t' -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }
}
