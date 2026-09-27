package com.tmstoner.silvermeme.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.RecurrenceFrequency
import com.tmstoner.silvermeme.data.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun RecurrenceRule.localizedSummary(): String {
    val locale = Locale.getDefault()
    return when (frequency) {
        RecurrenceFrequency.NONE -> stringResource(R.string.schedule_summary_none)
        RecurrenceFrequency.DAILY -> stringResource(R.string.schedule_summary_daily)
        RecurrenceFrequency.WEEKLY -> {
            if (daysOfWeek.isEmpty()) {
                stringResource(R.string.schedule_summary_weekly)
            } else {
                val days = daysOfWeek.sortedBy(DayOfWeek::getValue)
                    .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
                stringResource(R.string.schedule_summary_weekdays, days)
            }
        }
        RecurrenceFrequency.MONTHLY -> dayOfMonth?.let {
            stringResource(R.string.schedule_summary_monthly_day, it)
        } ?: stringResource(R.string.schedule_summary_monthly)
        RecurrenceFrequency.CUSTOM -> {
            val unitRes = when (intervalUnit) {
                ChronoUnit.WEEKS -> R.plurals.recurrence_weeks
                ChronoUnit.MONTHS -> R.plurals.recurrence_months
                else -> R.plurals.recurrence_days
            }
            val unit = pluralStringResource(unitRes, intervalCount)
            stringResource(R.string.schedule_summary_custom, intervalCount, unit)
        }
    }
}
