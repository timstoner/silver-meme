package com.tmstoner.silvermeme.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tmstoner.silvermeme.data.model.ChecklistItem
import com.tmstoner.silvermeme.data.model.LevelOfEffort
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.RecurrenceRule
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.model.cleaned
import com.tmstoner.silvermeme.data.storage.MarkdownFileManager
import com.tmstoner.silvermeme.ui.components.ChecklistEditor
import com.tmstoner.silvermeme.ui.components.ChecklistSaver
import com.tmstoner.silvermeme.ui.components.RecurrenceDialog
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val DATE_DISPLAY = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * Screen for creating a new TODO item or editing an existing one.
 *
 * Always visible: Title, Priority (segmented), Due date (quick chips), Notes, Checklist.
 * When editing, the top bar also offers Mark done/Reopen and Move to trash.
 * Behind the "Show more options" toggle (auto-expanded when editing a task that
 * already uses them): Level of Effort, Project/folder, Location, Tags, Schedule.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodoDetailScreen(
    viewModel: TodoViewModel,
    existingTodo: TodoItem? = null,
    onBack: () -> Unit
) {
    val availableProjects by viewModel.availableProjects.collectAsStateWithLifecycle()
    val isEditing = existingTodo != null

    // Fix 4: rememberSaveable for all form fields, keyed on existingTodo?.id
    // so that editing a different task resets the form.
    var title    by rememberSaveable(existingTodo?.id) { mutableStateOf(existingTodo?.title   ?: "") }
    var notes    by rememberSaveable(existingTodo?.id) { mutableStateOf(existingTodo?.content ?: "") }
    var dueDate  by rememberSaveable(existingTodo?.id) { mutableStateOf(existingTodo?.dueDate) }
    var priority by rememberSaveable(existingTodo?.id) { mutableStateOf(existingTodo?.priority ?: Priority.MEDIUM) }
    var location by rememberSaveable(existingTodo?.id) { mutableStateOf(existingTodo?.location ?: "") }
    var loe      by rememberSaveable(existingTodo?.id) { mutableStateOf(existingTodo?.loe ?: 0) }
    var project  by rememberSaveable(existingTodo?.id) { mutableStateOf(existingTodo?.project ?: "") }

    // Fix 4: tags stored as a comma-separated string for Saveable compatibility
    var tagsString by rememberSaveable(existingTodo?.id) {
        mutableStateOf(existingTodo?.tags?.joinToString(",") ?: "")
    }
    val tags: List<String> = tagsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    // Fix 4: recurrence stored as its storage string for Saveable compatibility
    var recurrenceString by rememberSaveable(existingTodo?.id) {
        mutableStateOf(existingTodo?.recurrence ?: "none")
    }
    val recurrenceRule = RecurrenceRule.parse(recurrenceString)

    var checklist by rememberSaveable(existingTodo?.id, stateSaver = ChecklistSaver) {
        mutableStateOf(existingTodo?.checklist ?: emptyList())
    }

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
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showLoeMenu        by remember { mutableStateOf(false) }
    var showProjectMenu    by remember { mutableStateOf(false) }

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
            checklist   = checklist.cleaned()
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
                  recurrenceString != (existingTodo?.recurrence ?: "none")    ||
                  checklist        != (existingTodo?.checklist  ?: emptyList<ChecklistItem>())

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showTrashDialog   by remember { mutableStateOf(false) }

    fun addTag() {
        val cleaned = tagInput.trim().trimEnd(',').trim().lowercase().replace(' ', '-')
        if (cleaned.isNotEmpty() && !tags.contains(cleaned)) {
            tagsString = (tags + cleaned).joinToString(",")
        }
        tagInput = ""
    }

    // Fix 5: intercept system back if there are unsaved changes
    BackHandler(enabled = isDirty) {
        showDiscardDialog = true
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
                    // Fix 5: guard nav icon as well
                    IconButton(onClick = { if (isDirty) showDiscardDialog = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (existingTodo != null) {
                        // Acts on the saved task (so recurrence/rename handling stays in
                        // the ViewModel); save or discard pending edits first.
                        IconButton(
                            onClick  = { viewModel.toggleComplete(existingTodo); onBack() },
                            enabled  = !isDirty
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = if (existingTodo.isCompleted) "Reopen task" else "Mark done"
                            )
                        }
                        IconButton(onClick = { showTrashDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Move to trash")
                        }
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
            // -- Title
            OutlinedTextField(
                value         = title,
                onValueChange = { newVal -> title = newVal; titleError = false },
                label         = { Text("Title *") },
                isError       = titleError,
                supportingText = if (titleError) {
                    { Text("Title is required") }
                } else null,
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            // -- Priority (one tap)
            Column {
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Priority.entries.forEachIndexed { index, p ->
                        SegmentedButton(
                            selected = priority == p,
                            onClick  = { priority = p },
                            shape    = SegmentedButtonDefaults.itemShape(index, Priority.entries.size),
                            icon     = {}
                        ) {
                            Text(
                                p.label.replaceFirstChar { c -> c.uppercaseChar() },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // -- Due date (whole field opens the picker; chips for common choices)
            val dateFieldInteraction = remember { MutableInteractionSource() }
            LaunchedEffect(dateFieldInteraction) {
                dateFieldInteraction.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) showDatePicker = true
                }
            }
            Column {
                OutlinedTextField(
                    value             = dueDate?.format(DATE_DISPLAY) ?: "",
                    onValueChange     = {},
                    readOnly          = true,
                    label             = { Text("Due date") },
                    placeholder       = { Text("None") },
                    leadingIcon       = { Icon(Icons.Filled.Event, contentDescription = null) },
                    trailingIcon      = if (dueDate != null) {
                        {
                            IconButton(onClick = { dueDate = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear date")
                            }
                        }
                    } else null,
                    interactionSource = dateFieldInteraction,
                    modifier          = Modifier.fillMaxWidth()
                )
                val today = LocalDate.now()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Today"     to today,
                        "Tomorrow"  to today.plusDays(1),
                        "Next week" to today.plusWeeks(1)
                    ).forEach { (label, date) ->
                        FilterChip(
                            selected = dueDate == date,
                            onClick  = { dueDate = if (dueDate == date) null else date },
                            label    = { Text(label) }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick  = { showDatePicker = true },
                        label    = { Text("Pick…") }
                    )
                }
            }

            // -- Notes (markdown body)
            OutlinedTextField(
                value         = notes,
                onValueChange = { newVal -> notes = newVal },
                label         = { Text("Notes (Markdown)") },
                minLines      = 4,
                modifier      = Modifier.fillMaxWidth()
            )

            // -- Checklist (saved as `- [ ]` / `- [x]` lines in the markdown body)
            ChecklistEditor(
                items         = checklist,
                onItemsChange = { checklist = it }
            )

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
                    if (showOptionalFields) "Hide options" else "Show more options",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    if (showOptionalFields) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (showOptionalFields) "Hide options" else "Show more options",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (showOptionalFields) {
                // -- Schedule (recurrence)
                OutlinedTextField(
                    value         = recurrenceRule.summary(),
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Schedule") },
                    leadingIcon   = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                    trailingIcon  = { TextButton(onClick = { showScheduleDialog = true }) { Text("Edit") } },
                    modifier      = Modifier.fillMaxWidth()
                )

                // -- Level of Effort (LOE)
                ExposedDropdownMenuBox(
                    expanded        = showLoeMenu,
                    onExpandedChange = { expanded -> showLoeMenu = expanded }
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

                // -- Project / folder
                val hasProjects = availableProjects.isNotEmpty()
                ExposedDropdownMenuBox(
                    expanded        = showProjectMenu && hasProjects,
                    onExpandedChange = { expanded -> showProjectMenu = expanded }
                ) {
                    OutlinedTextField(
                        value         = project,
                        onValueChange = { newVal -> project = newVal; showProjectMenu = true },
                        label         = { Text("Project / folder") },
                        placeholder   = { Text("None") },
                        trailingIcon  = if (hasProjects) {
                            { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProjectMenu) }
                        } else null,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        supportingText = { Text("Group related tasks together") }
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
                    label         = { Text("Location") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )

                // -- Tags
                Column {
                    Text("Tags", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { tag ->
                            AssistChip(
                                onClick      = {
                                    tagsString = (tags - tag).joinToString(",")
                                },
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
                            value           = tagInput,
                            onValueChange   = { newVal ->
                                tagInput = newVal
                                if (newVal.endsWith(",")) addTag()
                            },
                            label           = { Text("Add tag") },
                            singleLine      = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addTag() }),
                            modifier        = Modifier.weight(1f)
                        )
                        IconButton(onClick = ::addTag) {
                            Icon(Icons.Filled.Add, contentDescription = "Add tag")
                        }
                    }
                }
            }

            Spacer(Modifier.height(72.dp)) // Room for FAB
        }
    }

    // Fix 5: discard-changes confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title   = { Text("Discard changes?") },
            text    = { Text("Your unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            }
        )
    }

    // -- Move-to-trash confirmation (soft delete; restorable from the Trash screen)
    if (showTrashDialog && existingTodo != null) {
        AlertDialog(
            onDismissRequest = { showTrashDialog = false },
            title   = { Text("Move to trash?") },
            text    = { Text("You can restore it from Trash for 30 days.") },
            confirmButton = {
                TextButton(onClick = {
                    showTrashDialog = false
                    viewModel.trashTodo(existingTodo)
                    onBack()
                }) { Text("Move to trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showTrashDialog = false }) { Text("Cancel") }
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
