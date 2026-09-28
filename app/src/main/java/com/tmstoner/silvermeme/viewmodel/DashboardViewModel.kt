package com.tmstoner.silvermeme.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tmstoner.silvermeme.data.model.DashboardCalculator
import com.tmstoner.silvermeme.data.model.DashboardLayout
import com.tmstoner.silvermeme.data.model.DashboardLayoutSerializer
import com.tmstoner.silvermeme.data.model.DashboardProjectHealth
import com.tmstoner.silvermeme.data.model.DashboardSummary
import com.tmstoner.silvermeme.data.model.DashboardDensity as StoredDensity
import com.tmstoner.silvermeme.data.model.DashboardPreset as StoredPreset
import com.tmstoner.silvermeme.data.model.DashboardSection as StoredSection
import com.tmstoner.silvermeme.data.model.DashboardSectionLayout
import com.tmstoner.silvermeme.data.model.DashboardTimeRange as StoredTimeRange
import com.tmstoner.silvermeme.data.model.Project
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.repository.GitRepository
import com.tmstoner.silvermeme.data.repository.TodoDataSource
import com.tmstoner.silvermeme.data.storage.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.tmstoner.silvermeme.R

data class DashboardSyncStatus(
    val remoteConfigured: Boolean = false,
    val pending: Boolean = false,
    val error: String? = null,
    val conflictFiles: List<String> = emptyList(),
    val isRetrying: Boolean = false,
    /** Device-local epoch timestamp of the last successful remote sync. */
    val lastSuccessfulSyncTime: Long? = null,
    /** Configured device-wide maximum age for a successful remote sync. */
    val staleThresholdMillis: Long = SettingsStore.DEFAULT_SYNC_STALE_THRESHOLD_MILLIS
) {
    val label: Int get() = R.string.dashboard_sync_status
    val detail: String? get() = error ?: conflictFiles.takeIf { it.isNotEmpty() }?.joinToString()
    /**
     * A local-only vault has no remote freshness expectation. A configured
     * remote without a recorded success is stale until its first sync.
     */
    val isStale: Boolean get() = isStaleAt(System.currentTimeMillis())

    fun isStaleAt(currentTimeMillis: Long): Boolean =
        remoteConfigured && (
            lastSuccessfulSyncTime == null ||
                currentTimeMillis - lastSuccessfulSyncTime >= staleThresholdMillis.coerceAtLeast(1L)
            )
}

data class DashboardUiState(
    val sections: List<DashboardSection> = listOf(
        DashboardSection(DashboardSectionType.TODAY, true, DashboardSectionSize.STANDARD),
        DashboardSection(DashboardSectionType.TASK_SUMMARY, true, DashboardSectionSize.STANDARD),
        DashboardSection(DashboardSectionType.PROJECT_HEALTH, true, DashboardSectionSize.STANDARD),
        DashboardSection(DashboardSectionType.SYNC_STATUS, true, DashboardSectionSize.STANDARD),
        DashboardSection(DashboardSectionType.QUICK_ACTIONS, true, DashboardSectionSize.COMPACT)
    ),
    val todayTasks: List<TodoItem> = emptyList(),
    val summary: DashboardSummary = DashboardSummary(),
    val projectHealth: List<DashboardProjectHealthItem> = emptyList(),
    val syncStatus: DashboardSyncStatus = DashboardSyncStatus(),
    val projectFilters: List<DashboardProjectFilter> = listOf(DashboardProjectFilter(null, R.string.dashboard_project_filter)),
    val selectedProjectId: String? = null,
    val timeRanges: List<DashboardTimeRange> = DashboardTimeRange.entries,
    val timeRange: DashboardTimeRange = DashboardTimeRange.WEEK,
    val presets: List<DashboardPreset> = DashboardPreset.entries,
    val activePreset: DashboardPreset? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

enum class DashboardSectionType(val label: Int, val stored: StoredSection) {
    TODAY(R.string.dashboard_today, StoredSection.TODAY),
    TASK_SUMMARY(R.string.dashboard_summary_heading, StoredSection.SUMMARY),
    PROJECT_HEALTH(R.string.dashboard_project_health, StoredSection.PROJECT_HEALTH),
    SYNC_STATUS(R.string.dashboard_sync_status, StoredSection.SYNC),
    QUICK_ACTIONS(R.string.dashboard_quick_actions, StoredSection.QUICK_ACTIONS)
}
enum class DashboardSectionSize(val label: Int, val stored: StoredDensity) {
    COMPACT(R.string.dashboard_today, StoredDensity.COMPACT),
    STANDARD(R.string.dashboard_summary_heading, StoredDensity.STANDARD),
    EXPANDED(R.string.dashboard_project_health, StoredDensity.EXPANDED)
}
data class DashboardSection(val type: DashboardSectionType, val visible: Boolean, val size: DashboardSectionSize) {
    val itemLimit: Int get() = when (size) { DashboardSectionSize.COMPACT -> 3; DashboardSectionSize.STANDARD -> 6; DashboardSectionSize.EXPANDED -> Int.MAX_VALUE }
}
enum class DashboardTimeRange(val label: Int, val stored: StoredTimeRange) {
    TODAY(R.string.dashboard_today, StoredTimeRange.TODAY),
    WEEK(R.string.dashboard_due_today, StoredTimeRange.WEEK),
    MONTH(R.string.dashboard_completed, StoredTimeRange.MONTH)
}
enum class DashboardPreset(val label: Int, val stored: StoredPreset) {
    DAILY_PLANNING(R.string.dashboard_today, StoredPreset.DAILY_PLANNING),
    PROJECT_REVIEW(R.string.dashboard_project_health, StoredPreset.PROJECT_REVIEW),
    MINIMAL(R.string.dashboard_summary_heading, StoredPreset.MINIMAL)
}
/**
 * [label] is retained for the resource-backed "all projects" option.
 * Project options expose their current markdown-derived name in [displayName].
 */
data class DashboardProjectFilter(
    val id: String?,
    val label: Int,
    val displayName: String? = null
)
data class DashboardProjectHealthItem(
    val projectId: String, val projectName: String, val openTaskCount: Int,
    val overdueTaskCount: Int, val remainingEffort: Int, val riskLabels: List<String>
)

/** Typed operations consumed by dashboard configuration UIs. */
sealed interface DashboardAction {
    data class SetSectionVisible(val section: DashboardSectionType, val visible: Boolean) : DashboardAction
    data class MoveSection(val section: DashboardSectionType, val newIndex: Int) : DashboardAction
    data class SetSectionSize(val section: DashboardSectionType, val size: DashboardSectionSize) : DashboardAction
    data class SetProjectFilter(val projectId: String?) : DashboardAction
    data class SetTimeRange(val range: DashboardTimeRange) : DashboardAction
    data class ApplyPreset(val preset: DashboardPreset) : DashboardAction
    data object ResetLayout : DashboardAction
    data object Sync : DashboardAction
}

/**
 * Local-only dashboard coordinator. It reads markdown-derived repository data
 * but never pulls, pushes, or otherwise initiates a Git operation.
 *
 * Call [reload] after a Todo/Project mutation until mutation state flows are
 * exposed by those view models. Repository automatic-sync events also trigger a
 * reload, which covers remote-backed writes once their local write completes.
 */
class DashboardViewModel(
    private val source: TodoDataSource,
    private val settings: SettingsStore,
    private val clock: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var vaultPath = ""
    private var layoutJob: Job? = null
    private var syncTimeJob: Job? = null
    private var currentLayout = DashboardLayout.DEFAULT
    private var projectPaths: Map<String, String> = emptyMap()

    init {
        // Start with defaults synchronously; DataStore's first cached emission
        // replaces it as soon as it is available.
        viewModelScope.launch {
            settings.vaultPath.collectLatest { path ->
                vaultPath = path
                layoutJob?.cancel()
                syncTimeJob?.cancel()
                layoutJob = launch {
                    settings.dashboardLayout(path).collect { serialized ->
                        val layout = DashboardLayoutSerializer.deserialize(serialized) ?: DashboardLayout.DEFAULT
                        currentLayout = layout
                        _uiState.update { state ->
                            state.copy(
                                sections = layout.sections.toUiSections(),
                                timeRange = DashboardTimeRange.entries.first { range -> range.stored == layout.timeRange },
                                selectedProjectId = projectPaths.entries.firstOrNull { it.value == layout.projectFilter }?.key
                            )
                        }
                        reload()
                    }
                }
                syncTimeJob = launch {
                    settings.lastSuccessfulSyncTime(path).collect { timestamp ->
                        _uiState.update { state ->
                            state.copy(syncStatus = state.syncStatus.copy(lastSuccessfulSyncTime = timestamp))
                        }
                    }
                }
                viewModelScope.launch {
                    settings.syncStaleThresholdMillis.collect { threshold ->
                        _uiState.update { state ->
                            state.copy(
                                syncStatus = state.syncStatus.copy(
                                    staleThresholdMillis = threshold.coerceAtLeast(1L)
                                )
                            )
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(settings.gitRemoteUrl, source.pendingSync) { remote, pending -> remote.isNotBlank() to pending }
                .collect { (configured, pending) ->
                    _uiState.update { state -> state.copy(syncStatus = state.syncStatus.copy(remoteConfigured = configured, pending = pending)) }
                }
        }
        viewModelScope.launch {
            source.automaticSyncEvents.collect { result ->
                if (result is GitRepository.GitResult.Success) recordSuccessfulSync()
                _uiState.update { state ->
                    state.copy(syncStatus = when (result) {
                        GitRepository.GitResult.Success -> state.syncStatus.copy(error = null, conflictFiles = emptyList(), isRetrying = false)
                        is GitRepository.GitResult.Error -> state.syncStatus.copy(error = result.message, conflictFiles = emptyList(), isRetrying = false)
                        is GitRepository.GitResult.Conflict -> state.syncStatus.copy(error = null, conflictFiles = result.files, isRetrying = false)
                    })
                }
                reload()
            }
        }
        reload()
    }

    /** Reloads only local repository data; it intentionally does not call pull/push. */
    fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val todos = source.getTodos()
                val projects = source.getProjects()
                todos to projects
            }.onSuccess { (todos, projects) ->
                publish(todos, projects)
            }.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false, errorMessage = throwable.message ?: "Unable to load dashboard") }
            }
        }
    }

    fun onAction(action: DashboardAction) {
        val next = when (action) {
            is DashboardAction.SetSectionVisible -> updateSection(action.section) { it.copy(visible = action.visible) }
            is DashboardAction.SetSectionSize -> updateSection(action.section) { it.copy(density = action.size.stored) }
            is DashboardAction.MoveSection -> {
                val sections = currentLayout.normalized().sections.toMutableList()
                val from = sections.indexOfFirst { it.section == action.section.stored }
                if (from >= 0) sections.apply {
                    add(action.newIndex.coerceIn(0, lastIndex), removeAt(from))
                }
                currentLayout.copy(sections = sections)
            }
            is DashboardAction.SetProjectFilter -> {
                _uiState.update { it.copy(selectedProjectId = action.projectId, activePreset = null) }
                currentLayout.copy(projectFilter = projectPaths[action.projectId])
            }
            is DashboardAction.SetTimeRange -> {
                _uiState.update { it.copy(timeRange = action.range, activePreset = null) }
                currentLayout.copy(timeRange = action.range.stored)
            }
            is DashboardAction.ApplyPreset -> {
                _uiState.update { it.copy(activePreset = action.preset) }
                DashboardLayout.preset(action.preset.stored)
            }
            DashboardAction.ResetLayout -> DashboardLayout.DEFAULT
            DashboardAction.Sync -> {
                sync()
                return
            }
        }.normalized()
        persistLayout(next)
    }

    /** Backwards-compatible action entry point for consumers wired during migration. */
    fun dispatch(action: DashboardAction) = onAction(action)

    /** Retries an already-pending sync only when explicitly requested by the UI. */
    fun retryPendingSync() {
        synchronize(retryPending = true)
    }

    /**
     * Explicitly synchronizes the dashboard's local data with its remote.
     * Pending writes use the durable pull-then-push retry; otherwise this is a
     * standard manual pull.
     */
    fun sync() {
        synchronize(retryPending = _uiState.value.syncStatus.pending)
    }

    private fun synchronize(retryPending: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(syncStatus = it.syncStatus.copy(isRetrying = true, error = null, conflictFiles = emptyList())) }
            when (val result = if (retryPending) source.retryPendingSync() else source.pull()) {
                GitRepository.GitResult.Success -> {
                    recordSuccessfulSync()
                    _uiState.update {
                        it.copy(
                            syncStatus = it.syncStatus.copy(
                                isRetrying = false,
                                error = null,
                                conflictFiles = emptyList()
                            )
                        )
                    }
                }
                is GitRepository.GitResult.Error -> _uiState.update {
                    it.copy(
                        syncStatus = it.syncStatus.copy(
                            isRetrying = false,
                            error = result.message,
                            conflictFiles = emptyList()
                        )
                    )
                }
                is GitRepository.GitResult.Conflict -> _uiState.update {
                    it.copy(
                        syncStatus = it.syncStatus.copy(
                            isRetrying = false,
                            error = null,
                            conflictFiles = result.files
                        )
                    )
                }
            }
            reload()
        }
    }

    private fun updateSection(
        section: DashboardSectionType,
        transform: (DashboardSectionLayout) -> DashboardSectionLayout
    ): DashboardLayout = currentLayout.normalized().let { layout ->
        layout.copy(sections = layout.sections.map { if (it.section == section.stored) transform(it) else it })
    }

    private fun persistLayout(layout: DashboardLayout) {
        currentLayout = layout
        _uiState.update { it.copy(sections = layout.sections.toUiSections()) }
        viewModelScope.launch {
            settings.setDashboardLayout(vaultPath, DashboardLayoutSerializer.serialize(layout))
        }
    }

    private fun publish(todos: List<TodoItem>, projects: List<Project>) {
        projectPaths = projects.associate { it.id to it.path }
        val layout = currentLayout
        val selectedTodos = layout.projectFilter?.let { selected ->
            todos.filter { it.project == selected || it.project.startsWith("$selected/") }
        } ?: todos
        val today = clock()
        _uiState.update {
            it.copy(
                todayTasks = DashboardCalculator.todayOpenTasks(selectedTodos, today),
                summary = DashboardCalculator.summary(selectedTodos, today, layout.timeRange),
                projectHealth = DashboardCalculator.projectHealth(
                    projects.filter { layout.projectFilter == null || it.path == layout.projectFilter || it.path.startsWith("${layout.projectFilter}/") },
                    selectedTodos, today, layout.timeRange
                ).map { health -> health.toUiItem() },
                projectFilters = listOf(DashboardProjectFilter(null, R.string.dashboard_project_filter)) +
                    projects.filter { it.status == com.tmstoner.silvermeme.data.model.ProjectStatus.ACTIVE || it.status == com.tmstoner.silvermeme.data.model.ProjectStatus.ON_HOLD }
                        .sortedBy { it.name.lowercase() }.map {
                            DashboardProjectFilter(it.id, R.string.dashboard_project_filter, it.name)
                        },
                isLoading = false,
                errorMessage = null
            )
        }
    }

    private suspend fun recordSuccessfulSync() {
        val timestamp = System.currentTimeMillis()
        settings.setLastSuccessfulSyncTime(vaultPath, timestamp)
        _uiState.update { state ->
            state.copy(syncStatus = state.syncStatus.copy(lastSuccessfulSyncTime = timestamp))
        }
    }

    private fun List<DashboardSectionLayout>.toUiSections(): List<DashboardSection> =
        mapNotNull { layout ->
            DashboardSectionType.entries.firstOrNull { it.stored == layout.section }?.let { type ->
                DashboardSection(
                    type = type,
                    visible = layout.visible,
                    size = DashboardSectionSize.entries.first { it.stored == layout.density }
                )
            }
        }

    private fun DashboardProjectHealth.toUiItem(): DashboardProjectHealthItem =
        DashboardProjectHealthItem(
            projectId = project.id,
            projectName = project.name,
            openTaskCount = openTasks,
            overdueTaskCount = overdueTasks,
            remainingEffort = remainingEffort,
            riskLabels = risks.map { it.reason }
        )

    class Factory(
        private val source: TodoDataSource,
        private val settings: SettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DashboardViewModel(source, settings) as T
    }
}
