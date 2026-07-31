package com.tmstoner.silvermeme.data.model

/**
 * Priority levels for a TODO item, stored as lowercase strings in YAML frontmatter.
 */
enum class Priority(val label: String, val sortOrder: Int) {
    LOW("low", 0),
    MEDIUM("medium", 1),
    HIGH("high", 2),
    URGENT("urgent", 3);

    companion object {
        fun fromString(value: String?): Priority = when (value?.lowercase()?.trim()) {
            "low" -> LOW
            "medium", "normal" -> MEDIUM
            "high" -> HIGH
            "urgent", "critical" -> URGENT
            else -> MEDIUM
        }
    }
}
