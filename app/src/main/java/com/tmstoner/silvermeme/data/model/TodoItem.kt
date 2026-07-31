package com.tmstoner.silvermeme.data.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Represents a single TODO item backed by an Obsidian-compatible markdown file.
 *
 * The markdown file uses YAML frontmatter to store structured metadata:
 * ---
 * id: <uuid>
 * title: <string>
 * status: open|done
 * priority: low|medium|high|urgent
 * due: YYYY-MM-DD
 * location: <string>
 * tags:
 *   - tag1
 *   - tag2
 * created: YYYY-MM-DDTHH:MM:SS
 * updated: YYYY-MM-DDTHH:MM:SS
 * ---
 *
 * Optional markdown body follows the closing --- delimiter.
 */
data class TodoItem(
    /** Unique identifier (stored in frontmatter; defaults to filename without extension). */
    val id: String,
    /** Human-readable title of the task. Also used to derive the markdown filename. */
    val title: String,
    /** Free-form markdown body (anything after the closing --- delimiter). */
    val content: String = "",
    /** Optional due date for this task. */
    val dueDate: LocalDate? = null,
    /** Importance/priority level. */
    val priority: Priority = Priority.MEDIUM,
    /** Optional physical or logical location associated with this task. */
    val location: String? = null,
    /** Arbitrary Obsidian-compatible tags. */
    val tags: List<String> = emptyList(),
    /** True if status == "done". */
    val isCompleted: Boolean = false,
    /**
     * Path of the backing markdown file relative to the vault root.
     * Example: "Tasks/Buy groceries.md" or "Tasks/Home/Buy groceries.md"
     */
    val filePath: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    /** Subtasks as checklist items (Track E1). Rendered as markdown `- [ ]` / `- [x]` in body. */
    val checklist: List<ChecklistItem> = emptyList(),
    /** Recurrence pattern for this task (Track E2). Values: "daily", "weekly", "monthly", "none". */
    val recurrence: String = "none",
    /**
     * Level of Effort estimate (Fibonacci scale, agile-style story points).
     * Valid values: 0 (not estimated), 1, 2, 3, 5, 8, 13.
     */
    val loe: Int = 0
) {
    /** Returns a copy marked as complete/incomplete. */
    fun withCompletion(completed: Boolean) = copy(isCompleted = completed, updatedAt = LocalDateTime.now())
    
    /** Derives the project folder from filePath (Track E3). Empty string for root-level Tasks. */
    val project: String get() {
        val parts = filePath.split(Regex("""[\\/]"""))
        return when {
            parts.size > 2 -> parts.subList(1, parts.size - 1).joinToString("/")
            else -> ""
        }
    }
}

/** Allowed Level of Effort values (Fibonacci scale) and their human-readable labels. */
enum class LevelOfEffort(val points: Int, val label: String) {
    NONE(0, "Not estimated"),
    TRIVIAL(1, "1 · Trivial"),
    QUICK(2, "2 · Quick"),
    SMALL(3, "3 · Small"),
    MEDIUM(5, "5 · Medium"),
    LARGE(8, "8 · Large"),
    VERY_LARGE(13, "13 · Very large");

    companion object {
        fun fromPoints(points: Int): LevelOfEffort = entries.firstOrNull { it.points == points } ?: NONE
    }
}

/** A single checklist item within a TodoItem. */
data class ChecklistItem(
    val text: String,
    val isDone: Boolean = false
)
