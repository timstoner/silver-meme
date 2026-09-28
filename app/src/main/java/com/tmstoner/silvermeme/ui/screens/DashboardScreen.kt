package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.viewmodel.DashboardAction
import com.tmstoner.silvermeme.viewmodel.DashboardSection
import com.tmstoner.silvermeme.viewmodel.DashboardSectionSize
import com.tmstoner.silvermeme.viewmodel.DashboardSectionType
import com.tmstoner.silvermeme.viewmodel.DashboardUiState
import com.tmstoner.silvermeme.viewmodel.DashboardViewModel
import java.text.DateFormat
import java.util.Date
import java.time.LocalDate

enum class DashboardTaskDestination { ALL, TODAY, OVERDUE, COMPLETED }

/**
 * The dashboard deliberately only renders the dashboard state.  Persisting a layout,
 * calculating risk, and synchronizing data remain responsibilities of [DashboardViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    isTablet: Boolean,
    onOpenNavigationDrawer: () -> Unit,
    onOpenTasks: (DashboardTaskDestination) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onNewTask: () -> Unit,
    onNewProject: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        isTablet = isTablet,
        onAction = viewModel::onAction,
        onOpenNavigationDrawer = onOpenNavigationDrawer,
        onOpenTasks = onOpenTasks,
        onOpenTask = onOpenTask,
        onOpenProject = onOpenProject,
        onNewTask = onNewTask,
        onNewProject = onNewProject
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    state: DashboardUiState,
    isTablet: Boolean,
    onAction: (DashboardAction) -> Unit,
    onOpenNavigationDrawer: () -> Unit,
    onOpenTasks: (DashboardTaskDestination) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onNewTask: () -> Unit,
    onNewProject: () -> Unit
) {
    var showCustomizer by rememberSaveable { mutableStateOf(false) }
    val columns = if (isTablet) GridCells.Adaptive(280.dp) else GridCells.Fixed(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.destination_dashboard)) },
                navigationIcon = {
                    IconButton(onClick = onOpenNavigationDrawer) {
                        Icon(Icons.Default.Menu, stringResource(R.string.cd_open_navigation))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCustomizer = !showCustomizer },
                        modifier = Modifier.semantics { role = Role.Button }
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            stringResource(
                                if (showCustomizer) R.string.action_hide_dashboard_customizer
                                else R.string.action_customize_dashboard
                            )
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showCustomizer) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DashboardCustomizer(state, onAction)
                }
            }
            if (state.isLoading && state.sections.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.dashboard_loading))
                    }
                }
            }
            state.errorMessage?.let { message ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.dashboard_error, message),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            items(
                items = state.sections.filter { it.visible },
                key = { it.type.name },
                span = { section ->
                    if (section.size == DashboardSectionSize.EXPANDED) {
                        GridItemSpan(maxLineSpan)
                    } else GridItemSpan(1)
                }
            ) { section ->
                DashboardSectionCard(
                    section = section,
                    state = state,
                    onAction = onAction,
                    onOpenTasks = onOpenTasks,
                    onOpenTask = onOpenTask,
                    onOpenProject = onOpenProject,
                    onNewTask = onNewTask,
                    onNewProject = onNewProject
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardSectionCard(
    section: DashboardSection,
    state: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    onOpenTasks: (DashboardTaskDestination) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onNewTask: () -> Unit,
    onNewProject: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (section.type) {
                DashboardSectionType.TODAY -> {
                    SectionHeading(R.string.dashboard_today)
                    if (state.todayTasks.isEmpty()) Text(stringResource(R.string.dashboard_today_empty))
                    state.todayTasks.take(section.itemLimit).forEach { task ->
                        TextButton(
                            onClick = { onOpenTask(task.id) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(task.title)
                                Text(
                                    text = stringResource(
                                        if (task.dueDate?.isBefore(LocalDate.now()) == true) {
                                            R.string.state_task_overdue
                                        } else {
                                            R.string.dashboard_due_today
                                        }
                                    ),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    TextButton(onClick = { onOpenTasks(DashboardTaskDestination.TODAY) }) {
                        Text(stringResource(R.string.dashboard_view_today))
                    }
                }
                DashboardSectionType.PROJECT_HEALTH -> {
                    SectionHeading(R.string.dashboard_project_health)
                    if (state.projectHealth.isEmpty()) Text(stringResource(R.string.dashboard_projects_empty))
                    state.projectHealth.take(section.itemLimit).forEach { project ->
                        OutlinedButton(
                            onClick = { onOpenProject(project.projectId) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(project.projectName)
                                Text(
                                    stringResource(
                                        R.string.dashboard_project_health_summary,
                                        project.openTaskCount,
                                        project.overdueTaskCount,
                                        project.remainingEffort
                                    )
                                )
                                if (project.riskLabels.isNotEmpty()) {
                                    Text(
                                        stringResource(
                                            R.string.dashboard_risks,
                                            project.riskLabels.joinToString()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                DashboardSectionType.QUICK_ACTIONS -> {
                    SectionHeading(R.string.dashboard_quick_actions)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onNewTask, modifier = Modifier.heightIn(min = 48.dp).weight(1f)) {
                            Icon(Icons.Default.Add, null)
                            Text(stringResource(R.string.action_add_task))
                        }
                        OutlinedButton(onClick = onNewProject, modifier = Modifier.heightIn(min = 48.dp).weight(1f)) {
                            Text(stringResource(R.string.action_create_project))
                        }
                    }
                    OutlinedButton(
                        onClick = { onAction(DashboardAction.Sync) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Text(stringResource(R.string.action_sync))
                    }
                }
                DashboardSectionType.TASK_SUMMARY -> {
                    SectionHeading(R.string.dashboard_summary_heading)
                    DashboardMetric(R.string.dashboard_open_tasks, state.summary.openTasks) {
                        onOpenTasks(DashboardTaskDestination.ALL)
                    }
                    DashboardMetric(R.string.dashboard_due_today, state.summary.dueToday) {
                        onOpenTasks(DashboardTaskDestination.TODAY)
                    }
                    DashboardMetric(R.string.dashboard_overdue, state.summary.overdue) {
                        onOpenTasks(DashboardTaskDestination.OVERDUE)
                    }
                    DashboardMetric(R.string.dashboard_completed, state.summary.completedInRange) {
                        onOpenTasks(DashboardTaskDestination.COMPLETED)
                    }
                }
                DashboardSectionType.SYNC_STATUS -> {
                    SectionHeading(R.string.dashboard_sync_status)
                    val syncText = when {
                        state.syncStatus.isRetrying -> stringResource(R.string.dashboard_sync_in_progress)
                        state.syncStatus.error != null -> stringResource(
                            R.string.dashboard_sync_failed,
                            state.syncStatus.error
                        )
                        state.syncStatus.conflictFiles.isNotEmpty() -> stringResource(
                            R.string.sync_conflicts_message,
                            state.syncStatus.conflictFiles.size
                        )
                        state.syncStatus.pending -> stringResource(R.string.pending_sync_message)
                        state.syncStatus.remoteConfigured -> stringResource(R.string.dashboard_sync_up_to_date)
                        else -> stringResource(R.string.dashboard_sync_not_configured)
                    }
                    Text(syncText)
                    if (state.syncStatus.isStale) {
                        // Freshness has a text label; it is not communicated by
                        // color alone.
                        Text(
                            stringResource(R.string.dashboard_sync_stale),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    state.syncStatus.lastSuccessfulSyncTime?.let { timestamp ->
                        // This text conveys the sync recency independently of
                        // color, including for assistive technologies.
                        Text(
                            stringResource(
                                R.string.dashboard_sync_last_successful,
                                DateFormat.getDateTimeInstance(
                                    DateFormat.MEDIUM,
                                    DateFormat.SHORT
                                ).format(Date(timestamp))
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = { onAction(DashboardAction.Sync) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.action_sync)) }
                }
            }
        }
    }
}

@Composable
private fun DashboardMetric(label: Int, value: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(stringResource(R.string.dashboard_metric_value, stringResource(label), value))
    }
}

@Composable
private fun SectionHeading(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardCustomizer(state: DashboardUiState, onAction: (DashboardAction) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(R.string.dashboard_customize_heading)
            Text(stringResource(R.string.dashboard_customize_hint))
            Text(stringResource(R.string.dashboard_project_filter), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.projectFilters.forEach { project ->
                    FilterChip(
                        selected = state.selectedProjectId == project.id,
                        onClick = { onAction(DashboardAction.SetProjectFilter(project.id)) },
                        label = {
                            Text(
                                project.displayName
                                    ?: stringResource(
                                        if (project.id == null) R.string.filter_all_projects
                                        else project.label
                                    )
                            )
                        }
                    )
                }
            }
            Text(stringResource(R.string.dashboard_time_range), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.timeRanges.forEach { range ->
                    FilterChip(
                        selected = state.timeRange == range,
                        onClick = { onAction(DashboardAction.SetTimeRange(range)) },
                        label = { Text(stringResource(range.label)) }
                    )
                }
            }
            state.sections.forEachIndexed { index, section ->
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = section.visible,
                        onClick = { onAction(DashboardAction.SetSectionVisible(section.type, !section.visible)) },
                        label = { Text(stringResource(section.type.label)) }
                    )
                    TextButton(
                        enabled = index > 0,
                        onClick = { onAction(DashboardAction.MoveSection(section.type, index - 1)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.action_move_up)) }
                    TextButton(
                        enabled = index < state.sections.lastIndex,
                        onClick = { onAction(DashboardAction.MoveSection(section.type, index + 1)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.action_move_down)) }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardSectionSize.entries.forEach { size ->
                        FilterChip(
                            selected = section.size == size,
                            onClick = { onAction(DashboardAction.SetSectionSize(section.type, size)) },
                            label = { Text(stringResource(size.label)) }
                        )
                    }
                }
            }
            Text(stringResource(R.string.dashboard_presets), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.presets.forEach { preset ->
                    FilterChip(
                        selected = state.activePreset == preset,
                        onClick = { onAction(DashboardAction.ApplyPreset(preset)) },
                        label = { Text(stringResource(preset.label)) }
                    )
                }
            }
            TextButton(
                onClick = { onAction(DashboardAction.ResetLayout) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.action_reset_dashboard)) }
        }
    }
}
