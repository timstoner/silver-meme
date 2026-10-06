package com.tmstoner.silvermeme.util

import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.storage.MarkdownFileManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Parses a one-line quick-add string such as `Buy milk tomorrow !high #errand`
 * into a title plus optional due date, priority and tags.
 *
 * Recognised tokens (case-insensitive, removed from the title):
 *  - `#tag`                                  → tag (lowercased)
 *  - `!low` `!med` `!medium` `!high` `!urgent` → priority
 *  - `today`, `tomorrow`/`tmr`, `next week`, a full weekday name (`friday`)
 *    or a safe abbreviation (`tue`, `thu`, `fri`), or an ISO date
 *    (`2026-10-12`)                          → due date; the last one wins
 *
 * A weekday means its next occurrence, counting today. Everything else stays in
 * the title in its original order.
 */
object QuickAddParser {

    data class Result(
        val title: String,
        val dueDate: LocalDate? = null,
        val priority: Priority? = null,
        val tags: List<String> = emptyList()
    ) {
        /** True when the input produced a usable title. */
        val isValid: Boolean get() = title.isNotBlank()

        /**
         * Builds a new [TodoItem], placed in [project]'s folder when given.
         * Priority defaults to [Priority.MEDIUM], matching the detail screen.
         */
        fun toTodoItem(project: String? = null): TodoItem {
            val filename = "${MarkdownFileManager.sanitizeFilename(title)}.md"
            val cleanedProject = project?.trim().orEmpty()
            return TodoItem(
                id        = UUID.randomUUID().toString(),
                title     = title,
                dueDate   = dueDate,
                priority  = priority ?: Priority.MEDIUM,
                tags      = tags,
                filePath  = if (cleanedProject.isNotBlank()) "Tasks/$cleanedProject/$filename" else "Tasks/$filename",
                createdAt = LocalDateTime.now()
            )
        }
    }

    private val PRIORITIES = mapOf(
        "low" to Priority.LOW,
        "med" to Priority.MEDIUM,
        "medium" to Priority.MEDIUM,
        "high" to Priority.HIGH,
        "urgent" to Priority.URGENT
    )

    // Full names plus abbreviations that aren't ordinary words ("sun", "sat",
    // "wed", "mon" are excluded so "Buy sun cream" keeps its title).
    private val WEEKDAYS: Map<String, DayOfWeek> =
        DayOfWeek.entries.associateBy { it.name.lowercase() } + mapOf(
            "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
            "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY
        )

    fun parse(input: String, today: LocalDate = LocalDate.now()): Result {
        val words = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val titleWords = mutableListOf<String>()
        val tags = mutableListOf<String>()
        var priority: Priority? = null
        var dueDate: LocalDate? = null

        var i = 0
        while (i < words.size) {
            val word = words[i]
            val lower = word.lowercase()
            when {
                lower.length > 1 && lower.startsWith("#") -> {
                    val tag = lower.drop(1)
                    if (tag !in tags) tags += tag
                }
                lower.startsWith("!") && PRIORITIES.containsKey(lower.drop(1)) -> {
                    priority = PRIORITIES.getValue(lower.drop(1))
                }
                lower == "next" && words.getOrNull(i + 1)?.lowercase() == "week" -> {
                    dueDate = today.plusWeeks(1)
                    i++ // consume "week"
                }
                lower == "today" -> dueDate = today
                lower == "tomorrow" || lower == "tmr" -> dueDate = today.plusDays(1)
                WEEKDAYS.containsKey(lower) -> {
                    dueDate = today.with(TemporalAdjusters.nextOrSame(WEEKDAYS.getValue(lower)))
                }
                ISO_DATE.matches(lower) && parseIsoDate(lower) != null -> {
                    dueDate = parseIsoDate(lower)
                }
                else -> titleWords += word
            }
            i++
        }

        return Result(
            title    = titleWords.joinToString(" "),
            dueDate  = dueDate,
            priority = priority,
            tags     = tags
        )
    }

    private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    private fun parseIsoDate(text: String): LocalDate? =
        try { LocalDate.parse(text) } catch (_: DateTimeParseException) { null }
}
