package com.tmstoner.silvermeme.util

import java.net.URLEncoder

/**
 * Obsidian-style wikilinks in task notes: `[[Note]]`, `[[Note#Heading]]`,
 * `[[Note|shown text]]` (and embeds, `![[Note]]`, which share the syntax).
 *
 * Pure Kotlin so it can be unit-tested; the notes field in the task form uses it
 * for `[[` autocomplete and for the "linked notes" chips.
 */
object WikiLinks {

    data class Link(
        /** Note name the link points at, e.g. "Meeting notes". */
        val target: String,
        val heading: String? = null,
        val alias: String? = null
    )

    /** An unfinished `[[query` ending at the cursor; [start] is the index of the first `[`. */
    data class OpenLink(val start: Int, val query: String)

    // [[target]] with optional #heading and |alias; no brackets or newlines inside.
    private val LINK = Regex("""\[\[([^\[\]|#\n]+)(?:#([^\[\]|\n]*))?(?:\|([^\[\]\n]*))?]]""")

    /** All links in [text], in order, with surrounding whitespace trimmed from each part. */
    fun find(text: String): List<Link> =
        LINK.findAll(text).mapNotNull { m ->
            val target = m.groupValues[1].trim()
            if (target.isEmpty()) return@mapNotNull null
            Link(
                target  = target,
                heading = m.groupValues[2].trim().ifEmpty { null },
                alias   = m.groupValues[3].trim().ifEmpty { null }
            )
        }.toList()

    /** Distinct link targets in [text], first occurrence order (case-insensitive). */
    fun targets(text: String): List<String> =
        find(text).map { it.target }.distinctBy { it.lowercase() }

    /**
     * The link being typed at [cursor], or null when the cursor isn't inside an
     * unfinished `[[…`. Typing `|`, `#`, `]` or a newline ends the note-name part,
     * so suggestions stop there.
     */
    fun openLinkAt(text: String, cursor: Int): OpenLink? {
        if (cursor < 2 || cursor > text.length) return null
        val start = text.lastIndexOf("[[", startIndex = cursor - 2)
        if (start < 0) return null
        val query = text.substring(start + 2, cursor)
        if (query.any { it == ']' || it == '[' || it == '|' || it == '#' || it == '\n' }) return null
        return OpenLink(start, query)
    }

    /**
     * Up to [limit] of [names] matching [query], case-insensitive: names starting with
     * the query first, then names containing it, each group alphabetical.
     */
    fun suggest(query: String, names: List<String>, limit: Int = 5): List<String> {
        val q = query.trim().lowercase()
        val unique = names.distinctBy { it.lowercase() }
        val prefix = unique.filter { it.lowercase().startsWith(q) }.sortedBy { it.lowercase() }
        val contains = unique.filter { !it.lowercase().startsWith(q) && it.lowercase().contains(q) }
            .sortedBy { it.lowercase() }
        return (prefix + contains).take(limit)
    }

    /**
     * Replaces the open link with `[[name]]` and returns the new text and cursor
     * (just after the closing brackets). A `]]` already right after the cursor,
     * e.g. from an editor that auto-closes brackets, is reused rather than doubled.
     */
    fun complete(text: String, cursor: Int, open: OpenLink, name: String): Pair<String, Int> {
        val after = text.substring(cursor).removePrefix("]]")
        val link = "[[$name]]"
        return (text.substring(0, open.start) + link + after) to (open.start + link.length)
    }

    /**
     * `obsidian://open` URI for [target] in [vaultName]. Obsidian names a vault after
     * its folder by default, so callers pass the vault directory's name.
     */
    fun obsidianUri(vaultName: String, target: String): String =
        "obsidian://open?vault=${encode(vaultName)}&file=${encode(target)}"

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
