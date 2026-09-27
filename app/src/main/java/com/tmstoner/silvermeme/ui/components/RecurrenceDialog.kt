package com.tmstoner.silvermeme.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.RecurrenceFrequency
import com.tmstoner.silvermeme.data.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale

/**
 * Dialog for configuring a [RecurrenceRule] ("Schedule" feature) with cron-like options:
 * daily, weekly (optionally pinned to specific days), monthly (optionally pinned to a
 * day-of-month), or a custom "every N days/weeks/months" interval.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceDialog(
    initial: RecurrenceRule,
    onConfirm: (RecurrenceRule) -> Unit,
    onDismiss: () -> Unit
) {
    var frequency     by remember { mutableStateOf(initial.frequency) }
    var selectedDays  by remember { mutableStateOf(initial.daysOfWeek) }
    var dayOfMonth    by remember { mutableStateOf(initial.dayOfMonth?.toString() ?: "") }
    var intervalCount by remember { mutableStateOf(initial.intervalCount.toString()) }
    var intervalUnit  by remember { mutableStateOf(initial.intervalUnit) }
    var showUnitMenu   by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_schedule)) },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    RecurrenceFrequency.entries.forEach { freq ->
                        Row(
                            modifier = Modifier
                                .selectable(
                                    selected = frequency == freq,
                                    onClick  = { frequency = freq },
                                    role     = Role.RadioButton
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = frequency == freq, onClick = { frequency = freq })
                            Text(freq.localizedLabel(), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                if (frequency == RecurrenceFrequency.WEEKLY) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.schedule_repeat_on_optional), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = selectedDays.contains(day),
                                onClick  = {
                                    selectedDays = if (selectedDays.contains(day)) selectedDays - day
                                                   else selectedDays + day
                                },
                                label = {
                                    Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()))
                                }
                            )
                        }
                    }
                }

                if (frequency == RecurrenceFrequency.MONTHLY) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value           = dayOfMonth,
                        onValueChange   = { v -> dayOfMonth = v.filter { it.isDigit() }.take(2) },
                        label           = { Text(stringResource(R.string.schedule_day_of_month_optional)) },
                        placeholder     = { Text(stringResource(R.string.schedule_same_as_due_date)) },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                if (frequency == RecurrenceFrequency.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.schedule_every))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value           = intervalCount,
                            onValueChange   = { v -> intervalCount = v.filter { it.isDigit() }.take(3) },
                            singleLine      = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier        = Modifier.width(72.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        ExposedDropdownMenuBox(
                            expanded         = showUnitMenu,
                            onExpandedChange = { showUnitMenu = it }
                        ) {
                            OutlinedTextField(
                                value         = unitLabel(intervalUnit, intervalCount.toIntOrNull() ?: 1),
                                onValueChange = {},
                                readOnly      = true,
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUnitMenu) },
                                modifier      = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded         = showUnitMenu,
                                onDismissRequest = { showUnitMenu = false }
                            ) {
                                listOf(ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS).forEach { unit ->
                                    DropdownMenuItem(
                                        text    = { Text(unitLabel(unit, 2)) },
                                        onClick = { intervalUnit = unit; showUnitMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rule = RecurrenceRule(
                    frequency     = frequency,
                    daysOfWeek    = if (frequency == RecurrenceFrequency.WEEKLY) selectedDays else emptySet(),
                    dayOfMonth    = if (frequency == RecurrenceFrequency.MONTHLY) dayOfMonth.toIntOrNull()?.coerceIn(1, 31) else null,
                    intervalCount = intervalCount.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    intervalUnit  = intervalUnit
                )
                onConfirm(rule)
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun RecurrenceFrequency.localizedLabel(): String = stringResource(
    when (this) {
        RecurrenceFrequency.NONE -> R.string.schedule_frequency_none
        RecurrenceFrequency.DAILY -> R.string.schedule_frequency_daily
        RecurrenceFrequency.WEEKLY -> R.string.schedule_frequency_weekly
        RecurrenceFrequency.MONTHLY -> R.string.schedule_frequency_monthly
        RecurrenceFrequency.CUSTOM -> R.string.schedule_frequency_custom
    }
)

@Composable
private fun unitLabel(unit: ChronoUnit, count: Int): String = when (unit) {
    ChronoUnit.DAYS -> pluralStringResource(R.plurals.recurrence_days, count)
    ChronoUnit.WEEKS -> pluralStringResource(R.plurals.recurrence_weeks, count)
    ChronoUnit.MONTHS -> pluralStringResource(R.plurals.recurrence_months, count)
    else -> pluralStringResource(R.plurals.recurrence_days, count)
}
