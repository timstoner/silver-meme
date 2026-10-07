package com.tmstoner.silvermeme.data.model

/**
 * Names of the notes in the vault, for `[[` wikilink suggestions (Track G5).
 *
 * @param vaultName the vault folder's name, which Obsidian uses as the vault name
 *   by default (needed for `obsidian://open` links).
 * @param noteNames every note's filename without `.md`, sorted case-insensitively.
 */
data class NoteIndex(
    val vaultName: String,
    val noteNames: List<String>
) {
    companion object {
        val EMPTY = NoteIndex(vaultName = "", noteNames = emptyList())
    }
}
