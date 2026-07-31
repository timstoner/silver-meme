package com.tmstoner.silvermeme.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** High-level recurrence pattern selectable in the schedule dialog. */
enum class RecurrenceFrequency(val label: String) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM("Custom interval")
}

/**
 * Cron-like recurrence configuration for a [TodoItem] (Phase 2 "Schedule" feature).
 *
 * Persisted as a single compact string in the `recurrence` frontmatter field so no
 * changes to the markdown schema or [TodoItem] shape are required:
 *  - "none"                          → [RecurrenceFrequency.NONE]
 *  - "daily"                         → [RecurrenceFrequency.DAILY]
 *  - "weekly" / "weekly:MON,WED,FRI" → [RecurrenceFrequency.WEEKLY] (optionally pinned to specific days)
 *  - "monthly" / "monthly:15"        → [RecurrenceFrequency.MONTHLY] (optionally pinned to a day-of-month)
 *  - "every:3:days"                  → [RecurrenceFrequency.CUSTOM] (interval count + unit)
 *
 * The legacy plain "daily"/"weekly"/"monthly"/"none" values (used before this feature)
 * remain valid and parse into the equivalent rule with no specific days/day-of-month set.
 */
data class RecurrenceRule(
    val frequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    /** For [RecurrenceFrequency.WEEKLY]: specific days to repeat on. Empty = same weekday as due date. */
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    /** For [RecurrenceFrequency.MONTHLY]: specific day-of-month (1-31). Null = same day-of-month as due date. */
    val dayOfMonth: Int? = null,
    /** For [RecurrenceFrequency.CUSTOM]: repeat every [intervalCount] [intervalUnit]. */
    val intervalCount: Int = 1,
    val intervalUnit: ChronoUnit = ChronoUnit.DAYS
) {
    val isRecurring: Boolean get() = frequency != RecurrenceFrequency.NONE

    /** Serializes this rule back into the compact storage string. */
    fun toStorageString(): String = when (frequency) {
        RecurrenceFrequency.NONE -> "none"
        RecurrenceFrequency.DAILY -> "daily"
        RecurrenceFrequency.WEEKLY ->
            if (daysOfWeek.isEmpty()) "weekly"
            else "weekly:" + daysOfWeek.sortedBy { it.value }.joinToString(",") { it.abbrev() }
        RecurrenceFrequency.MONTHLY ->
            if (dayOfMonth == null) "monthly" else "monthly:$dayOfMonth"
        RecurrenceFrequency.CUSTOM ->
            "every:$intervalCount:${intervalUnit.name.lowercase()}"
    }

    /** Short human-readable summary shown on the Schedule button, e.g. "Weekly on Mon, Wed". */
    fun summary(): String = when (frequency) {
        RecurrenceFrequency.NONE -> "Does not repeat"
        RecurrenceFrequency.DAILY -> "Repeats daily"
        RecurrenceFrequency.WEEKLY ->
            if (daysOfWeek.isEmpty()) "Repeats weekly"
            else "Weekly on " + daysOfWeek.sortedBy { it.value }.joinToString(", ") { it.shortLabel() }
        RecurrenceFrequency.MONTHLY ->
            if (dayOfMonth == null) "Repeats monthly" else "Monthly on day $dayOfMonth"
        RecurrenceFrequency.CUSTOM ->
            "Every $intervalCount ${intervalUnit.name.lowercase().let { if (intervalCount == 1) it.removeSuffix("s") else it }}"
    }

    /** Computes the next due date after [from] according to this rule, or null if not recurring. */
    fun nextDueDate(from: LocalDate): LocalDate? = when (frequency) {
        RecurrenceFrequency.NONE -> null
        RecurrenceFrequency.DAILY -> from.plusDays(1)
        RecurrenceFrequency.WEEKLY ->
            if (daysOfWeek.isEmpty()) {
                from.plusWeeks(1)
            } else {
                (1..7).map { from.plusDays(it.toLong()) }
                    .firstOrNull { daysOfWeek.contains(it.dayOfWeek) }
                    ?: from.plusWeeks(1)
            }
        RecurrenceFrequency.MONTHLY -> {
            val next = from.plusMonths(1)
            if (dayOfMonth == null) next
            else {
                val clampedDay = minOf(dayOfMonth, next.lengthOfMonth())
                next.withDayOfMonth(clampedDay)
            }
        }
        RecurrenceFrequency.CUSTOM -> when (intervalUnit) {
            ChronoUnit.DAYS   -> from.plusDays(intervalCount.toLong())
            ChronoUnit.WEEKS  -> from.plusWeeks(intervalCount.toLong())
            ChronoUnit.MONTHS -> from.plusMonths(intervalCount.toLong())
            else              -> from.plusDays(intervalCount.toLong())
        }
    }

    companion object {
        val NONE = RecurrenceRule()

        /** Parses a storage string (new format or legacy plain value) into a [RecurrenceRule]. */
        fun parse(value: String): RecurrenceRule {
            val trimmed = value.trim()
            if (trimmed.isBlank() || trimmed == "none") return NONE

            val parts = trimmed.split(":")
            return when (parts[0].lowercase()) {
                "daily" -> RecurrenceRule(frequency = RecurrenceFrequency.DAILY)
                "weekly" -> RecurrenceRule(
                    frequency  = RecurrenceFrequency.WEEKLY,
                    daysOfWeek = parts.getOrNull(1)?.split(",")
                        ?.mapNotNull { parseDayAbbrev(it) }
                        ?.toSet() ?: emptySet()
                )
                "monthly" -> RecurrenceRule(
                    frequency  = RecurrenceFrequency.MONTHLY,
                    dayOfMonth = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 31)
                )
                "every" -> {
                    val count = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val unit = when (parts.getOrNull(2)?.lowercase()) {
                        "weeks"  -> ChronoUnit.WEEKS
                        "months" -> ChronoUnit.MONTHS
                        else     -> ChronoUnit.DAYS
                    }
                    RecurrenceRule(frequency = RecurrenceFrequency.CUSTOM, intervalCount = count, intervalUnit = unit)
                }
                else -> NONE
            }
        }

        private fun parseDayAbbrev(abbrev: String): DayOfWeek? = when (abbrev.trim().uppercase()) {
            "MON" -> DayOfWeek.MONDAY
            "TUE" -> DayOfWeek.TUESDAY
            "WED" -> DayOfWeek.WEDNESDAY
            "THU" -> DayOfWeek.THURSDAY
            "FRI" -> DayOfWeek.FRIDAY
            "SAT" -> DayOfWeek.SATURDAY
            "SUN" -> DayOfWeek.SUNDAY
            else  -> null
        }
    }
}

private fun DayOfWeek.abbrev(): String = when (this) {
    DayOfWeek.MONDAY -> "MON"
    DayOfWeek.TUESDAY -> "TUE"
    DayOfWeek.WEDNESDAY -> "WED"
    DayOfWeek.THURSDAY -> "THU"
    DayOfWeek.FRIDAY -> "FRI"
    DayOfWeek.SATURDAY -> "SAT"
    DayOfWeek.SUNDAY -> "SUN"
}

private fun DayOfWeek.shortLabel(): String = when (this) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}
