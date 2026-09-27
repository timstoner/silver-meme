package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.Project
import com.tmstoner.silvermeme.data.model.ProjectStatus
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.viewmodel.ProjectViewModel
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(viewModel: ProjectViewModel, onOpen: (Project) -> Unit, onCreate: () -> Unit, onOpenDrawer: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<ProjectStatus?>(null) }
    val shown = state.projects.filter {
        (status == null ||
            if (status == ProjectStatus.ARCHIVED) it.isArchived
            else !it.isArchived && it.status == status) &&
            it.name.contains(query, ignoreCase = true)
    }
    val openNavigation = stringResource(R.string.cd_open_navigation)
    val createProject = stringResource(R.string.action_create_project)
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.projects_title)) },
            navigationIcon = { IconButton(onClick = onOpenDrawer, Modifier.semantics { contentDescription = openNavigation }) { Icon(Icons.Default.Folder, null) } }) },
        floatingActionButton = { FloatingActionButton(onClick = onCreate, Modifier.semantics { contentDescription = createProject }) { Icon(Icons.Default.Add, null) } }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.action_search)) }) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(null, *ProjectStatus.entries.toTypedArray()).forEach { value ->
                    FilterChip(status == value, { status = value }, { Text(stringResource(statusLabel(value))) })
                }
            } }
            item { Text(stringResource(R.string.project_unassigned_count, state.unassignedCount)) }
            if (state.isLoading) item { CircularProgressIndicator(Modifier.padding(24.dp)) }
            state.error?.let { item { Text(stringResource(R.string.error_projects_load), color = MaterialTheme.colorScheme.error) } }
            items(shown, key = { it.id }) { project ->
                ProjectRow(project, state.metricsByProjectId[project.id], onOpen)
            }
            if (!state.isLoading && shown.isEmpty()) item { Text(stringResource(R.string.projects_empty), Modifier.padding(24.dp)) }
        }
    }
}

private fun statusLabel(status: ProjectStatus?): Int = when (status) {
    null -> R.string.filter_all_projects
    ProjectStatus.ACTIVE -> R.string.project_status_active
    ProjectStatus.ON_HOLD -> R.string.project_status_on_hold
    ProjectStatus.COMPLETED -> R.string.project_status_completed
    ProjectStatus.ARCHIVED -> R.string.project_status_archived
}

@Composable private fun ProjectRow(
    project: Project,
    metrics: com.tmstoner.silvermeme.data.model.ProjectMetrics?,
    onOpen: (Project) -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(project) }.semantics { contentDescription = project.name }) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Folder, null)
            Column { Text("${project.icon.orEmpty()} ${project.name}", style = MaterialTheme.typography.titleMedium)
                Text(stringResource(statusLabel(project.status)))
            metrics?.let {
                Text(
                    stringResource(
                        R.string.project_hub_metrics,
                        it.openCount,
                        it.completedCount,
                        it.overdueCount,
                        it.remainingEffort,
                        it.nearestDue?.toString() ?: stringResource(R.string.hint_none)
                    )
                )
            }
            Text(stringResource(R.string.project_target_date, project.targetDate?.toString() ?: stringResource(R.string.hint_none))) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    viewModel: ProjectViewModel,
    projectId: String,
    onBack: () -> Unit,
    onAddTask: (Project) -> Unit,
    onEdit: (Project) -> Unit
) {
    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val projects by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(projectId) { viewModel.loadDetail(projectId) }
    val project = state.project
    if (state.isLoading) { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    if (project == null) { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.project_not_found)) }; return }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var archiveWarning by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val metrics = state.metrics
    val moreOptions = stringResource(R.string.action_more_options)
    val addTask = stringResource(R.string.action_add_task)
    Scaffold(topBar = { TopAppBar(title = { Text(project.name) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
        actions = { IconButton({ menu = true }, Modifier.semantics { contentDescription = moreOptions }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.action_edit_project)) }, { menu = false; onEdit(project) })
                DropdownMenuItem({ Text(stringResource(R.string.action_rename_move_project)) }, { menu = false; rename = true })
                ProjectStatus.entries
                    .filter { it != ProjectStatus.ARCHIVED && it != project.status }
                    .forEach { status ->
                    DropdownMenuItem({ Text(stringResource(R.string.project_set_status, stringResource(statusLabel(status)))) }, { menu = false; viewModel.setStatus(project, status); viewModel.loadDetail(projectId) })
                }
                if (project.isArchived)
                    DropdownMenuItem({ Text(stringResource(R.string.action_restore_project)) }, { menu = false; viewModel.restore(project); onBack() })
                else DropdownMenuItem({ Text(stringResource(R.string.action_archive_project)) }, { menu = false; if ((metrics?.openCount ?: 0) > 0) archiveWarning = true else { viewModel.archive(project); onBack() } })
            }
        }) },
        floatingActionButton = { FloatingActionButton({ onAddTask(project) }, Modifier.semantics { contentDescription = addTask }) { Icon(Icons.Default.Add, null) } }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { project.description.takeIf { it.isNotBlank() }?.let { Text(it) } }
            item { Text(stringResource(R.string.project_target_date, project.targetDate?.toString() ?: stringResource(R.string.hint_none))) }
            metrics?.let { m -> item {
                Text(stringResource(R.string.project_progress, m.completedCount, m.openCount, m.completedEffort, m.remainingEffort))
                LinearProgressIndicator(progress = { m.progress }, Modifier.fillMaxWidth())
                Text(stringResource(R.string.project_remaining_upcoming, m.remainingEffort, m.nearestDue?.toString() ?: stringResource(R.string.hint_none)))
            } }
            item { HorizontalDivider(); Text(stringResource(R.string.project_tasks), style = MaterialTheme.typography.titleLarge) }
            state.todos.groupBy { it.dueDate }.toSortedMap(compareBy<LocalDate?> { it == null }.thenBy { it }).forEach { (date, todos) ->
                item { Text(date?.toString() ?: stringResource(R.string.group_no_due_date), style = MaterialTheme.typography.titleSmall) }
                items(todos, key = { it.id }) { todo ->
                    ProjectTaskRow(todo, todo.id in selected) { selected = selected.toggle(todo.id) }
                }
            }
            if (state.todos.isEmpty()) item { Text(stringResource(R.string.project_tasks_empty)) }
            if (selected.isNotEmpty()) item { Button({ moveDialog = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.action_move_selected_tasks, selected.size)) } }
        }
    }
    if (rename) RenameMoveDialog(project, state.todos.size, { name, parent -> viewModel.move(project, parent, name) { onBack() } }) { rename = false }
    if (archiveWarning) AlertDialog({ archiveWarning = false }, title = { Text(stringResource(R.string.project_archive_warning_title)) },
        text = { Text(stringResource(R.string.project_archive_warning_message, metrics?.openCount ?: 0)) },
        confirmButton = { TextButton({ viewModel.archive(project); onBack() }) { Text(stringResource(R.string.action_archive_project)) } },
        dismissButton = { TextButton({ archiveWarning = false }) { Text(stringResource(R.string.action_cancel)) } })
    if (moveDialog) MoveTasksDialog(projects.projects.filter { it.id != project.id }, {
        val todos = state.todos.filter { todo -> todo.id in selected }
        viewModel.moveTodos(todos, it) { selected = emptySet(); moveDialog = false; viewModel.loadDetail(projectId) }
    }, { name ->
        val todos = state.todos.filter { todo -> todo.id in selected }
        viewModel.createAndMoveTodos(name, todos) { selected = emptySet(); moveDialog = false; viewModel.loadDetail(projectId) }
    }) { moveDialog = false }
}

@Composable private fun ProjectTaskRow(todo: TodoItem, selected: Boolean, toggle: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = toggle).semantics { contentDescription = todo.title }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(selected, { toggle() })
            Column { Text(todo.title); Text(todo.dueDate?.toString() ?: stringResource(R.string.group_no_due_date)) }
        }
    }
}

@Composable private fun RenameMoveDialog(project: Project, count: Int, onSave: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(project.name) }; var parent by remember { mutableStateOf(project.path.substringBeforeLast('/', "")) }
    AlertDialog(onDismiss, title = { Text(stringResource(R.string.action_rename_move_project)) }, text = { Column {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.label_project_name)) })
        OutlinedTextField(parent, { parent = it }, label = { Text(stringResource(R.string.label_parent_project)) })
        Text(stringResource(R.string.project_affected_task_count, count))
    } }, confirmButton = { TextButton({ if (name.isNotBlank()) onSave(name, parent.ifBlank { null }) }) { Text(stringResource(R.string.action_save)) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable private fun MoveTasksDialog(projects: List<Project>, onMove: (String) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(onDismiss, title = { Text(stringResource(R.string.action_move_tasks)) }, text = { Column {
        projects.forEach { project -> TextButton({ onMove(project.path) }, Modifier.fillMaxWidth()) { Text(project.name) } }
        OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(R.string.label_new_destination_project)) })
        TextButton({ if (newName.isNotBlank()) onCreate(newName) }) { Text(stringResource(R.string.action_create_project)) }
    } }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorScreen(viewModel: ProjectViewModel, project: Project?, onBack: () -> Unit) {
    var name by rememberSaveable(project?.id) { mutableStateOf(project?.name.orEmpty()) }; var description by rememberSaveable(project?.id) { mutableStateOf(project?.description.orEmpty()) }
    var icon by rememberSaveable(project?.id) { mutableStateOf(project?.icon.orEmpty()) }; var color by rememberSaveable(project?.id) { mutableStateOf(project?.color.orEmpty()) }
    var target by rememberSaveable(project?.id) { mutableStateOf(project?.targetDate?.toString().orEmpty()) }; var tags by rememberSaveable(project?.id) { mutableStateOf(project?.tags?.joinToString(",").orEmpty()) }
    var loe by rememberSaveable(project?.id) { mutableStateOf(project?.loe?.toString() ?: "0") }; var recurrence by rememberSaveable(project?.id) { mutableStateOf(project?.recurrence ?: "none") }
    var priority by rememberSaveable(project?.id) { mutableStateOf(project?.defaultPriority?.name.orEmpty()) }; var reminder by rememberSaveable(project?.id) { mutableStateOf(project?.reminderEnabled ?: true) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(if (project == null) R.string.project_new else R.string.project_edit)) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_project_name)) }) }; item { OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_project_description)) }) }
            item { OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_target_date_yyyy_mm_dd)) }) }; item { OutlinedTextField(color, { color = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_project_color)) }) }
            item { OutlinedTextField(icon, { icon = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_project_icon)) }) }; item { OutlinedTextField(priority, { priority = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_default_priority)) }) }
            item { OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_tags)) }) }; item { OutlinedTextField(loe, { loe = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_loe)) }) }
            item { OutlinedTextField(recurrence, { recurrence = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_default_recurrence)) }) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Checkbox(reminder, { reminder = it }); Text(stringResource(R.string.label_default_reminder)) } }
            item { Button({ if (name.isNotBlank()) { val base = project ?: Project(UUID.randomUUID().toString(), name.trim(), name.trim()); viewModel.save(base.copy(name = name.trim(), description = description, targetDate = runCatching { LocalDate.parse(target) }.getOrNull(), color = color.ifBlank { null }, icon = icon.ifBlank { null }, defaultPriority = Priority.entries.firstOrNull { it.name.equals(priority, true) }, tags = tags.split(",").map(String::trim).filter(String::isNotEmpty), loe = loe.toIntOrNull() ?: 0, recurrence = recurrence, reminderEnabled = reminder)) { onBack() } } }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.action_save)) } }
        }
    }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id
