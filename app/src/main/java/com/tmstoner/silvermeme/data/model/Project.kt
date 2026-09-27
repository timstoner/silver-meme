package com.tmstoner.silvermeme.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** User-managed lifecycle state. Archiving is deliberately separate from this value. */
enum class ProjectStatus(val storageValue: String) {
    ACTIVE("active"),
    ON_HOLD("on-hold"),
    COMPLETED("completed"),
    ARCHIVED("archived");

    companion object {
        fun fromStorage(value: String?): ProjectStatus =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: ACTIVE
    }
}

/**
 * An Obsidian markdown-backed project. [path] is a validated path relative to
 * `Tasks/` (for example, `Work/Planning`), never an absolute filesystem path.
 */
data class Project(
    val id: String,
    val name: String,
    val path: String,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val description: String = "",
    val targetDate: LocalDate? = null,
    val color: String? = null,
    val icon: String? = null,
    val defaultPriority: Priority? = null,
    val tags: List<String> = emptyList(),
    val loe: Int = 0,
    val recurrence: String = "none",
    val reminderEnabled: Boolean? = null,
    val reminderTime: LocalTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    /** True when metadata and task files live below their dedicated archive roots. */
    val isArchived: Boolean = false,
    /** Backing metadata path relative to the vault, exposed for safe subsequent saves. */
    val filePath: String = ""
)

/** UI-neutral project health data calculated from non-trashed tasks. */
data class ProjectMetrics(
    val openCount: Int,
    val completedCount: Int,
    val overdueCount: Int,
    val remainingEffort: Int,
    val completedEffort: Int,
    val nearestDue: LocalDate?,
    val upcomingDates: List<LocalDate>,
    val progress: Float
)
