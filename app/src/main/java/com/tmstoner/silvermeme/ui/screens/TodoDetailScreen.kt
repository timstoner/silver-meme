package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.LevelOfEffort
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val DATE_DISPLAY = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun recurrenceLabel(value: String): String = when (value) {
    "daily"   -> "Daily"
    "weekly"  -> "Weekly"
    "monthly" -> "Monthly"
    else      -> "Does not repeat"
}

/**
 * Screen for creating a new TODO item or editing an existing one.
 *
 * Fields:
 *  - Title (required)
 *  - Notes (markdown body)
 *  - Due date (date picker)
 *  - Priority (dropdown)
 *  - Location
 *  - Tags (chip input)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodoDetailScreen(
    viewModel: TodoViewModel,
    existingTodo: TodoItem? = null,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isEditing = existingTodo != null

    // Form state
    var title       by remember { mutableStateOf(existingTodo?.title       ?: "") }
    var notes       by remember { mutableStateOf(existingTodo?.content     ?: "") }
    var dueDate     by remember { mutableStateOf(existingTodo?.dueDate) }
    var priority    by remember { mutableStateOf(existingTodo?.priority    ?: Priority.MEDIUM) }
    var location    by remember { mutableStateOf(existingTodo?.location    ?: "") }
    var tags        by remember { mutableStateOf(existingTodo?.tags        ?: emptyList()) }
    var tagInput    by remember { mutableStateOf("") }
    var titleError  by remember { mutableStateOf(false) }
    var recurrence  by remember { mutableStateOf(existingTodo?.recurrence  ?: "none") }
    var loe         by remember { mutableStateOf(existingTodo?.loe         ?: 0) }
    var project     by remember { mutableStateOf(existingTodo?.project     ?: "") }

    var showDatePicker    by remember { mutableStateOf(false) }
    var showPriorityMenu  by remember { mutableStateOf(false) }
    var showRecurrenceMenu by remember { mutableStateOf(false) }
    var showLoeMenu       by remember { mutableStateOf(false) }
    var showProjectMenu   by remember { mutableStateOf(false) }
    val availableProjects = viewModel.getAvailableProjects()

    // Navigate back after a successful save
    LaunchedEffect(uiState.isSaving) {
        if (!uiState.isSaving && isEditing) {
            // Don't auto-navigate for new todos to allow multi-step input
        }
    }

    /** Mirrors the sanitization rules in MarkdownFileManager.sanitizeFilename. */
    fun sanitizeForFilename(name: String): String =
        name.trim()
            .replace(Regex("""[/\\:*?"<>|#]"""), "")
            .take(150)
            .ifBlank { "Untitled" }

    fun buildTodo(): TodoItem {
        val cleanedProject = project.trim()
        val filename = "${sanitizeForFilename(title)}.md"
        val filePath = if (cleanedProject.isNotBlank()) "Tasks/$cleanedProject/$filename" else "Tasks/$filename"
        return TodoItem(
            id          = existingTodo?.id ?: UUID.randomUUID().toString(),
            title       = title.trim(),
            content     = notes.trim(),
            dueDate     = dueDate,
            priority    = priority,
            location    = location.trim().takeIf { it.isNotBlank() },
            tags        = tags,
            isCompleted = existingTodo?.isCompleted ?: false,
            filePath    = filePath,
            createdAt   = existingTodo?.createdAt ?: java.time.LocalDateTime.now(),
            recurrence  = recurrence,
            loe         = loe
        )
    }

    fun onSave() {
        if (title.isBlank()) { titleError = true; return }
        titleError = false
        viewModel.saveTodo(buildTodo(), previousFilePath = existingTodo?.filePath)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Task" else "New Task") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = ::onSave) {
                Icon(Icons.Filled.Done, contentDescription = "Save")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Title ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value         = title,
                onValueChange = { title = it; titleError = false },
                label         = { Text("Title *") },
                isError       = titleError,
                supportingText = if (titleError) {{ Text("Title is required") }} else null,
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            // ── Priority ──────────────────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded        = showPriorityMenu,
                onExpandedChange = { showPriorityMenu = it }
            ) {
                OutlinedTextField(
                    value           = priority.label.replaceFirstChar { it.uppercaseChar() },
                    onValueChange   = {},
                    readOnly        = true,
                    label           = { Text("Priority") },
                    trailingIcon    = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPriorityMenu) },
                    modifier        = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded        = showPriorityMenu,
                    onDismissRequest = { showPriorityMenu = false }
                ) {
                    Priority.entries.forEach { p ->
                        DropdownMenuItem(
                            text    = { Text(p.label.replaceFirstChar { it.uppercaseChar() }) },
                            onClick = { priority = p; showPriorityMenu = false }
                        )
                    }
                }
            }

            // ── Due date ──────────────────────────────────────────────────────
            OutlinedTextField(
                value         = dueDate?.format(DATE_DISPLAY) ?: "",
                onValueChange = {},
                readOnly      = true,
                label         = { Text("Due date") },
                trailingIcon  = {
                    Row {
                        if (dueDate != null) {
                            IconButton(onClick = { dueDate = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear date")
                            }
                        }
                        TextButton(onClick = { showDatePicker = true }) { Text("Pick") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Recurrence ────────────────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded        = showRecurrenceMenu,
                onExpandedChange = { showRecurrenceMenu = it }
            ) {
                OutlinedTextField(
                    value           = recurrenceLabel(recurrence),
                    onValueChange   = {},
                    readOnly        = true,
                    label           = { Text("Repeat") },
                    trailingIcon    = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRecurrenceMenu) },
                    modifier        = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded        = showRecurrenceMenu,
                    onDismissRequest = { showRecurrenceMenu = false }
                ) {
                    listOf("none", "daily", "weekly", "monthly").forEach { r ->
                        DropdownMenuItem(
                            text    = { Text(recurrenceLabel(r)) },
                            onClick = { recurrence = r; showRecurrenceMenu = false }
                        )
                    }
                }
            }

            // ── Level of Effort (LOE) ────────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded        = showLoeMenu,
                onExpandedChange = { showLoeMenu = it }
            ) {
                OutlinedTextField(
                    value           = LevelOfEffort.fromPoints(loe).label,
                    onValueChange   = {},
                    readOnly        = true,
                    label           = { Text("LOE") },
                    trailingIcon    = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showLoeMenu) },
                    modifier        = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded        = showLoeMenu,
                    onDismissRequest = { showLoeMenu = false }
                ) {
                    LevelOfEffort.entries.forEach { level ->
                        DropdownMenuItem(
                            text    = { Text(level.label) },
                            onClick = { loe = level.points; showLoeMenu = false }
                        )
                    }
                }
            }

            // ── Project / folder ──────────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded        = showProjectMenu && availableProjects.isNotEmpty(),
                onExpandedChange = { showProjectMenu = it }
            ) {
                OutlinedTextField(
                    value         = project,
                    onValueChange = { project = it; showProjectMenu = true },
                    label         = { Text("Project / folder") },
                    placeholder   = { Text("None") },
                    trailingIcon  = if (availableProjects.isNotEmpty()) {
                        { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProjectMenu) }
                    } else null,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    supportingText = { Text("Group related tasks together") }
                )
                if (availableProjects.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded        = showProjectMenu,
                        onDismissRequest = { showProjectMenu = false }
                    ) {
                        availableProjects.forEach { p ->
                            DropdownMenuItem(
                                text    = { Text(p) },
                                onClick = { project = p; showProjectMenu = false }
                            )
                        }
                    }
                }
            }

            // ── Location ──────────────────────────────────────────────────────
            OutlinedTextField(
                value         = location,
                onValueChange = { location = it },
                label         = { Text("Location") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            // ── Tags ──────────────────────────────────────────────────────────
            Column {
                Text("Tags", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        AssistChip(
                            onClick      = { tags = tags - tag },
                            label        = { Text("#$tag") },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove tag") }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value         = tagInput,
                        onValueChange = { tagInput = it },
                        label         = { Text("Add tag") },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick  = {
                            val cleaned = tagInput.trim().lowercase().replace(' ', '-')
                            if (cleaned.isNotEmpty() && !tags.contains(cleaned)) {
                                tags = tags + cleaned
                            }
                            tagInput = ""
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add tag")
                    }
                }
            }

            // ── Notes (markdown body) ─────────────────────────────────────────
            OutlinedTextField(
                value         = notes,
                onValueChange = { notes = it },
                label         = { Text("Notes (Markdown)") },
                minLines      = 4,
                modifier      = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(72.dp)) // Room for FAB
        }
    }

    // ── Date picker dialog ─────────────────────────────────────────────────────
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate
                ?.atStartOfDay(ZoneId.of("UTC"))
                ?.toInstant()
                ?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dueDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
