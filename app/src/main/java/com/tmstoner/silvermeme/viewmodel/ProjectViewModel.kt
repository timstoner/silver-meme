package com.tmstoner.silvermeme.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tmstoner.silvermeme.data.model.Project
import com.tmstoner.silvermeme.data.model.ProjectMetrics
import com.tmstoner.silvermeme.data.model.ProjectStatus
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.repository.TodoDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectUiState(
    val projects: List<Project> = emptyList(),
    val metricsByProjectId: Map<String, ProjectMetrics> = emptyMap(),
    val unassignedCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)
data class ProjectDetailUiState(
    val project: Project? = null,
    val todos: List<TodoItem> = emptyList(),
    val metrics: ProjectMetrics? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

/** Presentation state for the persisted project metadata API. */
class ProjectViewModel(private val source: TodoDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()
    private val _detailState = MutableStateFlow(ProjectDetailUiState())
    val detailState: StateFlow<ProjectDetailUiState> = _detailState.asStateFlow()

    init { reload() }

    fun reload() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching {
            val projects = source.getProjects()
            val unassigned = source.getTodos().count { it.project.isBlank() }
            val metrics = projects.associate { project ->
                project.id to source.getProjectMetrics(project.path)
            }
            Triple(projects, metrics, unassigned)
        }.onSuccess { (projects, metrics, unassigned) ->
            _uiState.value = ProjectUiState(
                projects = projects,
                metricsByProjectId = metrics,
                unassignedCount = unassigned,
                isLoading = false
            )
        }.onFailure { error -> _uiState.update { it.copy(isLoading = false, error = error.message) } }
    }

    fun save(project: Project, onSaved: (Project) -> Unit = {}) = viewModelScope.launch {
        runCatching { source.saveProject(project) }.onSuccess { saved -> reload(); onSaved(saved) }
            .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
    }
    fun loadDetail(projectId: String) = viewModelScope.launch {
        _detailState.value = ProjectDetailUiState(isLoading = true)
        runCatching {
            val project = source.getProject(projectId)
            requireNotNull(project)
            Triple(project, source.getTodosForProject(project.path), source.getProjectMetrics(project.path))
        }.onSuccess { (project, todos, metrics) ->
            _detailState.value = ProjectDetailUiState(project, todos, metrics, isLoading = false)
        }.onFailure { error ->
            _detailState.value = ProjectDetailUiState(isLoading = false, error = error.message)
        }
    }
    fun setStatus(project: Project, status: ProjectStatus) = save(project.copy(status = status))
    fun archive(project: Project) = viewModelScope.launch {
        runCatching { source.archiveProject(project.id) }.onSuccess { reload() }
            .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
    }
    fun restore(project: Project) = viewModelScope.launch {
        runCatching { source.restoreProject(project.id) }.onSuccess { reload() }
            .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
    }
    fun move(project: Project, parent: String?, name: String, onMoved: () -> Unit = {}) = viewModelScope.launch {
        runCatching { source.moveProject(project.id, parent, name) }.onSuccess { reload(); onMoved() }
            .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
    }
    fun moveTodos(todos: List<TodoItem>, destinationPath: String, onMoved: () -> Unit = {}) = viewModelScope.launch {
        runCatching { source.moveTodosToProject(todos, destinationPath) }.onSuccess { onMoved(); reload() }
            .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
    }
    fun createAndMoveTodos(
        name: String,
        todos: List<TodoItem>,
        onMoved: () -> Unit = {}
    ) = viewModelScope.launch {
        runCatching {
            val project = source.saveProject(
                Project(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name.trim(),
                    path = name.trim()
                )
            )
            source.moveTodosToProject(todos, project.path)
        }.onSuccess { reload(); onMoved() }
            .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
    }
    fun projectTodos(path: String, onResult: (List<TodoItem>) -> Unit) = viewModelScope.launch {
        runCatching { source.getTodosForProject(path) }.onSuccess(onResult)
            .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
    }
    fun metrics(path: String, onResult: (ProjectMetrics) -> Unit) = viewModelScope.launch {
        runCatching { source.getProjectMetrics(path) }.onSuccess(onResult)
            .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
    }

    class Factory(private val source: TodoDataSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProjectViewModel(source) as T
    }
}
