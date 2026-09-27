package com.tmstoner.silvermeme.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.LevelOfEffort
import com.tmstoner.silvermeme.data.model.ChecklistItem
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.RecurrenceRule
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.model.Project
import com.tmstoner.silvermeme.data.storage.MarkdownFileManager
import com.tmstoner.silvermeme.notifications.NotificationScheduler
import com.tmstoner.silvermeme.ui.components.RecurrenceDialog
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import android.text.format.DateFormat
import java.util.Locale
import java.util.UUID

private fun encodeChecklist(items: List<ChecklistItem>): String =
    items.joinToString("\n") { item ->
        val encodedText = java.util.Base64.getEncoder()
            .encodeToString(item.text.toByteArray(Charsets.UTF_8))
        "${if (item.isDone) "1" else "0"}:$encodedText"
    }

private fun decodeChecklist(value: String): List<ChecklistItem> =
    value.lineSequence()
        .filter { it.length >= 2 && it[1] == ':' }
        .map { line ->
            val text = java.util.Base64.getDecoder().decode(line.substring(2))
                .toString(Charsets.UTF_8)
            ChecklistItem(text = text, isDone = line[0] == '1')
        }
        .toList()

/**
 * Screen for creating a new TODO item or editing an existing one.
 *
 * Always visible: Title, Priority, Due date, Notes.
 * Behind the "Show more options" toggle (auto-expanded when editing a task that
 * already uses them): Level of Effort, Project/folder, Location, Tags, Schedule.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodoDetailScreen(
    viewModel: TodoViewModel,
    existingTodo: TodoItem? = null,
    projectDefaults: Project? = null,
    onBack: () -> Unit
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val availableProjects by viewModel.availableProjects.collectAsStateWithLifecycle()
    val isEditing = existingTodo != null
    val context = LocalContext.current
    var reminderPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        reminderPermissionDenied = !granted
    }

    // Fix 4: rememberSaveable for all form fields, keyed on existingTodo?.id
    // so that editing a different task resets the form.
    var title    by rememberSaveable(existingTodo?.id) { mutableStateOf(existingTodo?.title   ?: "") }
    var notes    by rememberSaveable(existingTodo?.id) { mutableStateOf(existingTodo?.content ?: "") }
    var dueDate  by rememberSaveable(existingTodo?.id) { mutableStateOf(existingTodo?.dueDate) }
    var reminderEnabled by rememberSaveable(existingTodo?.id, projectDefaults?.id) {
        mutableStateOf(existingTodo?.reminderEnabled ?: projectDefaults?.reminderEnabled ?: true)
    }
    var reminderTimeText by rememberSaveable(existingTodo?.id) {
        mutableStateOf(existingTodo?.reminderTime?.toString() ?: projectDefaults?.reminderTime?.toString().orEmpty())
    }
    var priority by rememberSaveable(existingTodo?.id, projectDefaults?.id) { mutableStateOf(existingTodo?.priority ?: projectDefaults?.defaultPriority ?: Priority.MEDIUM) }
    var location by rememberSaveable(existingTodo?.id) { mutableStateOf(existingTodo?.location ?: "") }
    var loe      by rememberSaveable(existingTodo?.id, projectDefaults?.id) { mutableStateOf(existingTodo?.loe ?: projectDefaults?.loe ?: 0) }
    var project  by rememberSaveable(existingTodo?.id, projectDefaults?.id) { mutableStateOf(existingTodo?.project ?: projectDefaults?.path ?: "") }

    // Fix 4: tags stored as a comma-separated string for Saveable compatibility
    var tagsString by rememberSaveable(existingTodo?.id) {
        mutableStateOf(existingTodo?.tags?.joinToString(",") ?: projectDefaults?.tags?.joinToString(",").orEmpty())
    }
    val tags: List<String> = tagsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    // Fix 4: recurrence stored as its storage string for Saveable compatibility
    var recurrenceString by rememberSaveable(existingTodo?.id) {
        mutableStateOf(existingTodo?.recurrence ?: projectDefaults?.recurrence ?: "none")
    }
    val recurrenceRule = RecurrenceRule.parse(recurrenceString)
    var checklistState by rememberSaveable(existingTodo?.id) {
        mutableStateOf(encodeChecklist(existingTodo?.checklist.orEmpty()))
    }
    var checklistInput by rememberSaveable(existingTodo?.id) { mutableStateOf("") }
    val checklistItems = decodeChecklist(checklistState)

    var tagInput   by rememberSaveable(existingTodo?.id) { mutableStateOf("") }
    var titleError by remember { mutableStateOf(false) }

    // Whether any optional field already has non-default data (so editing an
    // existing task with those fields set doesn't hide them by default).
    val hasOptionalData = existingTodo != null && (
        existingTodo.loe > 0 ||
        existingTodo.project.isNotBlank() ||
        !existingTodo.location.isNullOrBlank() ||
        existingTodo.tags.isNotEmpty() ||
        existingTodo.recurrence != "none"
    )
    // showOptionalFields is transient UI state, not form data
    var showOptionalFields by remember { mutableStateOf(hasOptionalData) }

    // Transient dialog state — no need to survive rotation
    var showDatePicker     by remember { mutableStateOf(false) }
    var showPriorityMenu   by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showLoeMenu        by remember { mutableStateOf(false) }
    var showProjectMenu    by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }

    // Navigate back after a successful save
    LaunchedEffect(uiState.isSaving) {
        if (!uiState.isSaving && isEditing) {
            // Don't auto-navigate for new todos to allow multi-step input
        }
    }

    if (showReminderTimePicker) {
        val initialReminderTime = reminderTimeText.takeIf(String::isNotBlank)
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: LocalTime.MIDNIGHT
        val timePickerState = rememberTimePickerState(
            initialHour = initialReminderTime.hour,
            initialMinute = initialReminderTime.minute,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { showReminderTimePicker = false },
            title = { Text(stringResource(R.string.title_pick_reminder_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        reminderTimeText = LocalTime.of(
                            timePickerState.hour,
                            timePickerState.minute
                        ).toString()
                        showReminderTimePicker = false
                    }
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showReminderTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    fun buildTodo(): TodoItem {
        val cleanedProject = project.trim()
        // Fix 7: delegate sanitization to MarkdownFileManager (canonical source)
        val filename = "${MarkdownFileManager.sanitizeFilename(title.trim())}.md"
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
            recurrence  = recurrenceRule.toStorageString(),
            loe         = loe,
            // Fix 4 (P0-checklist UI side): preserve checklist from existing todo
            checklist = checklistItems,
            reminderEnabled = reminderEnabled,
            reminderTime = reminderTimeText.takeIf { it.isNotBlank() }?.let(LocalTime::parse)
        )
    }

    fun onSave() {
        if (title.isBlank()) { titleError = true; return }
        titleError = false
        viewModel.saveTodo(buildTodo(), previousFilePath = existingTodo?.filePath)
        onBack()
    }

    // Fix 5: dirty-state detection for unsaved-changes guard
    val isDirty = title            != (existingTodo?.title      ?: "")           ||
                  notes            != (existingTodo?.content    ?: "")           ||
                  dueDate          != existingTodo?.dueDate                       ||
                  priority         != (existingTodo?.priority   ?: Priority.MEDIUM) ||
                  location         != (existingTodo?.location   ?: "")           ||
                  loe              != (existingTodo?.loe        ?: 0)             ||
                  project          != (existingTodo?.project    ?: "")           ||
                  tagsString       != (existingTodo?.tags?.joinToString(",") ?: "") ||
                  recurrenceString != (existingTodo?.recurrence ?: "none") ||
                  checklistState   != encodeChecklist(existingTodo?.checklist.orEmpty()) ||
                  reminderEnabled != (existingTodo?.reminderEnabled ?: true) ||
                  reminderTimeText != (existingTodo?.reminderTime?.toString().orEmpty())

    var showDiscardDialog by remember { mutableStateOf(false) }

    // Fix 5: intercept system back if there are unsaved changes
    BackHandler(enabled = isDirty) {
        showDiscardDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEditing) R.string.title_edit_task else R.string.title_new_task
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    // Fix 5: guard nav icon as well
                    IconButton(onClick = { if (isDirty) showDiscardDialog = true else onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = ::onSave,
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    stringResource(
                        if (isEditing) R.string.action_save_task else R.string.action_create_task
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.isCtrlPressed && event.key == Key.S -> {
                            onSave()
                            true
                        }
                        event.key == Key.Escape -> {
                            if (isDirty) showDiscardDialog = true else onBack()
                            true
                        }
                        else -> false
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // -- Title
            OutlinedTextField(
                value         = title,
                onValueChange = { newVal -> title = newVal; titleError = false },
                label         = { Text(stringResource(R.string.label_title_required)) },
                isError       = titleError,
                supportingText = if (titleError) {
                    { Text(stringResource(R.string.error_title_required)) }
                } else null,
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.label_reminder), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.reminder_enabled), modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { enabled ->
                            reminderEnabled = enabled
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !NotificationScheduler.hasNotificationPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.reminder_enabled)
                        }
                    )
                }
                if (reminderEnabled) {
                    TextButton(
                        onClick = { showReminderTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val timeText = reminderTimeText.takeIf(String::isNotBlank)?.let {
                            runCatching {
                                LocalTime.parse(it).format(
                                    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                                        .withLocale(Locale.getDefault())
                                )
                            }.getOrNull()
                        }
                        Text(
                            text = timeText ?: stringResource(R.string.reminder_due_midnight),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (reminderTimeText.isNotBlank()) {
                        TextButton(
                            onClick = { reminderTimeText = "" },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text(stringResource(R.string.action_clear_time)) }
                    }
                }
            }

            // -- Priority
            ExposedDropdownMenuBox(
                expanded        = showPriorityMenu,
                onExpandedChange = { expanded -> showPriorityMenu = expanded }
            ) {
                OutlinedTextField(
                    value           = priority.localizedLabel(),
                    onValueChange   = {},
                    readOnly        = true,
                    label           = { Text(stringResource(R.string.label_priority)) },
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
                            text    = { Text(p.localizedLabel()) },
                            onClick = { priority = p; showPriorityMenu = false }
                        )
                    }
                }
            }

            // -- Due date
            OutlinedTextField(
                value         = dueDate?.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(Locale.getDefault())
                ) ?: "",
                onValueChange = {},
                readOnly      = true,
                label         = { Text(stringResource(R.string.label_due_date)) },
                trailingIcon  = {
                    Row {
                        if (dueDate != null) {
                            IconButton(onClick = { dueDate = null }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_clear_date)
                                )
                            }
                        }
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(stringResource(R.string.action_pick_date))
                        }
                    }
                },
                supportingText = if (reminderPermissionDenied) {
                    { Text(stringResource(R.string.reminder_permission_denied)) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )

            // -- Notes (markdown body)
            OutlinedTextField(
                value         = notes,
                onValueChange = { newVal -> notes = newVal },
                label         = { Text(stringResource(R.string.label_notes_markdown)) },
                minLines      = 4,
                modifier      = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.label_checklist),
                    style = MaterialTheme.typography.titleMedium
                )
                checklistItems.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.isDone,
                            onCheckedChange = { checked ->
                                checklistState = encodeChecklist(
                                    checklistItems.mapIndexed { itemIndex, current ->
                                        if (itemIndex == index) current.copy(isDone = checked) else current
                                    }
                                )
                            }
                        )
                        OutlinedTextField(
                            value = item.text,
                            onValueChange = { text ->
                                checklistState = encodeChecklist(
                                    checklistItems.mapIndexed { itemIndex, current ->
                                        if (itemIndex == index) current.copy(text = text) else current
                                    }
                                )
                            },
                            label = { Text(stringResource(R.string.label_checklist_item)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                checklistState = encodeChecklist(
                                    checklistItems.toMutableList().apply {
                                        add(index - 1, removeAt(index))
                                    }
                                )
                            },
                            enabled = index > 0
                        ) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = stringResource(R.string.action_move_checklist_item_up)
                            )
                        }
                        IconButton(
                            onClick = {
                                checklistState = encodeChecklist(
                                    checklistItems.toMutableList().apply {
                                        add(index + 1, removeAt(index))
                                    }
                                )
                            },
                            enabled = index < checklistItems.lastIndex
                        ) {
                            Icon(
                                Icons.Filled.ArrowDownward,
                                contentDescription = stringResource(R.string.action_move_checklist_item_down)
                            )
                        }
                        IconButton(
                            onClick = {
                                checklistState = encodeChecklist(
                                    checklistItems.filterIndexed { itemIndex, _ -> itemIndex != index }
                                )
                            }
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_remove_checklist_item)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = checklistInput,
                        onValueChange = { checklistInput = it },
                        label = { Text(stringResource(R.string.label_checklist_item)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val text = checklistInput.trim()
                            if (text.isNotEmpty()) {
                                checklistState = encodeChecklist(
                                    checklistItems + ChecklistItem(text)
                                )
                                checklistInput = ""
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.action_add_checklist_item)
                        )
                    }
                }
            }

            HorizontalDivider()

            // -- Show/hide optional fields
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showOptionalFields = !showOptionalFields },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(
                        if (showOptionalFields) R.string.action_hide_options
                        else R.string.action_show_options
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    if (showOptionalFields) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (showOptionalFields) R.string.action_hide_options
                        else R.string.action_show_options
                    ),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (showOptionalFields) {
                // -- Schedule (recurrence)
                OutlinedTextField(
                    value         = recurrenceRule.localizedSummary(),
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text(stringResource(R.string.label_schedule)) },
                    leadingIcon   = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                    trailingIcon  = {
                        TextButton(onClick = { showScheduleDialog = true }) {
                            Text(stringResource(R.string.action_edit))
                        }
                    },
                    modifier      = Modifier.fillMaxWidth()
                )

                // -- Level of Effort (LOE)
                ExposedDropdownMenuBox(
                    expanded        = showLoeMenu,
                    onExpandedChange = { expanded -> showLoeMenu = expanded }
                ) {
                    OutlinedTextField(
                        value           = LevelOfEffort.fromPoints(loe).localizedLabel(),
                        onValueChange   = {},
                        readOnly        = true,
                        label           = { Text(stringResource(R.string.label_loE)) },
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
                                text    = { Text(level.localizedLabel()) },
                                onClick = { loe = level.points; showLoeMenu = false }
                            )
                        }
                    }
                }

                // -- Project / folder
                val hasProjects = availableProjects.isNotEmpty()
                ExposedDropdownMenuBox(
                    expanded        = showProjectMenu && hasProjects,
                    onExpandedChange = { expanded -> showProjectMenu = expanded }
                ) {
                    OutlinedTextField(
                        value         = project,
                        onValueChange = { newVal -> project = newVal; showProjectMenu = true },
                        label         = { Text(stringResource(R.string.label_project_folder)) },
                        placeholder   = { Text(stringResource(R.string.hint_none)) },
                        trailingIcon  = if (hasProjects) {
                            { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProjectMenu) }
                        } else null,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        supportingText = { Text(stringResource(R.string.project_description)) }
                    )
                    if (hasProjects) {
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

                // -- Location
                OutlinedTextField(
                    value         = location,
                    onValueChange = { newVal -> location = newVal },
                    label         = { Text(stringResource(R.string.label_location)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )

                // -- Tags
                Column {
                    Text(stringResource(R.string.label_tags), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { tag ->
                            AssistChip(
                                onClick      = {
                                    tagsString = (tags - tag).joinToString(",")
                                },
                                label        = { Text("#$tag") },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove_tag))
                                }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value         = tagInput,
                            onValueChange = { newVal -> tagInput = newVal },
                            label         = { Text(stringResource(R.string.action_add_tag)) },
                            singleLine    = true,
                            modifier      = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val cleaned = tagInput.trim().lowercase().replace(' ', '-')
                                if (cleaned.isNotEmpty() && !tags.contains(cleaned)) {
                                    tagsString = (tags + cleaned).joinToString(",")
                                }
                                tagInput = ""
                            }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_tag))
                        }
                    }
                }
            }
        }
    }

    // Fix 5: discard-changes confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title   = { Text(stringResource(R.string.label_discard_changes)) },
            text    = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text(stringResource(R.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_keep_editing))
                }
            }
        )
    }

    // -- Date picker dialog
    if (showDatePicker) {
        val initialMillis = dueDate
            ?.atStartOfDay(ZoneId.of("UTC"))
            ?.toInstant()
            ?.toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dueDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        if (
                            reminderEnabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !NotificationScheduler.hasNotificationPermission(context)
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // -- Schedule (recurrence) dialog
    if (showScheduleDialog) {
        RecurrenceDialog(
            initial   = recurrenceRule,
            onConfirm = { rule ->
                recurrenceString = rule.toStorageString()
                showScheduleDialog = false
            },
            onDismiss = { showScheduleDialog = false }
        )
    }
}
