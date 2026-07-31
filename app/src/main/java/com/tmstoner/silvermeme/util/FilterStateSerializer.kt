package com.tmstoner.silvermeme.util

import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.viewmodel.FilterState
import com.tmstoner.silvermeme.viewmodel.SortOrder
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Serializes/deserializes [FilterState] to a simple delimited string for persistence
 * in [com.tmstoner.silvermeme.data.storage.SettingsStore.lastFilterState].
 *
 * Avoids pulling in a JSON library for a handful of scalar fields. Format:
 * `priority=<NAME|NONE>|showCompleted=<bool>|showOverdue=<bool>|searchQuery=<url-encoded>|sortOrder=<NAME>|project=<url-encoded|NONE>`
 */
object FilterStateSerializer {

    private const val NONE = "\u0000NONE\u0000"

    fun serialize(state: FilterState): String {
        val parts = listOf(
            "priority" to (state.priority?.name ?: NONE),
            "showCompleted" to state.showCompleted.toString(),
            "showOverdue" to state.showOverdue.toString(),
            "searchQuery" to encode(state.searchQuery),
            "sortOrder" to state.sortOrder.name,
            "project" to (state.project?.let { encode(it) } ?: NONE)
        )
        return parts.joinToString("|") { (k, v) -> "$k=$v" }
    }

    fun deserialize(serialized: String): FilterState? {
        if (serialized.isBlank()) return null
        return try {
            val map = serialized.split("|")
                .mapNotNull { entry ->
                    val idx = entry.indexOf('=')
                    if (idx == -1) null else entry.substring(0, idx) to entry.substring(idx + 1)
                }
                .toMap()

            FilterState(
                priority = map["priority"]?.takeIf { it != NONE }?.let { runCatching { Priority.valueOf(it) }.getOrNull() },
                showCompleted = map["showCompleted"]?.toBooleanStrictOrNull() ?: false,
                showOverdue = map["showOverdue"]?.toBooleanStrictOrNull() ?: false,
                searchQuery = map["searchQuery"]?.let { decode(it) } ?: "",
                sortOrder = map["sortOrder"]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.DUE_DATE_ASC,
                project = map["project"]?.takeIf { it != NONE }?.let { decode(it) }
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")
}
