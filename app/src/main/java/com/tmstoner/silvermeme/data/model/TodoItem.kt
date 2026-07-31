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
     * Example: "Tasks/Buy groceries.md"
     */
    val filePath: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /** Returns a copy marked as complete/incomplete. */
    fun withCompletion(completed: Boolean) = copy(isCompleted = completed, updatedAt = LocalDateTime.now())
}
