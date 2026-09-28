package com.tmstoner.silvermeme.data.model

import java.io.File
import java.time.LocalDate

/** User-configurable dashboard sections. Their identifiers are persistence-safe. */
enum class DashboardSection(val storageId: String) {
    TODAY("today"),
    SUMMARY("summary"),
    PROJECT_HEALTH("project_health"),
    SYNC("sync"),
    QUICK_ACTIONS("quick_actions");

    companion object {
        fun fromStorage(value: String): DashboardSection? =
            entries.firstOrNull { it.storageId == value }
    }
}

enum class DashboardDensity(val storageId: String) {
    COMPACT("compact"), STANDARD("standard"), EXPANDED("expanded");
    companion object { fun fromStorage(value: String) = entries.firstOrNull { it.storageId == value } }
}

enum class DashboardTimeRange(val storageId: String) {
    TODAY("today"), WEEK("week"), MONTH("month");

    fun start(today: LocalDate): LocalDate = when (this) {
        TODAY -> today
        WEEK -> today.minusDays(6)
        MONTH -> today.minusDays(29)
    }
    companion object { fun fromStorage(value: String) = entries.firstOrNull { it.storageId == value } }
}

enum class DashboardPreset(val storageId: String) {
    DAILY_PLANNING("daily_planning"), PROJECT_REVIEW("project_review"), MINIMAL("minimal")
}

data class DashboardSectionLayout(
    val section: DashboardSection,
    val visible: Boolean = true,
    val density: DashboardDensity = DashboardDensity.STANDARD
)

/** Device-only dashboard configuration. An empty [projectFilter] means all projects. */
data class DashboardLayout(
    val sections: List<DashboardSectionLayout> = defaultSections(),
    val projectFilter: String? = null,
    val timeRange: DashboardTimeRange = DashboardTimeRange.WEEK
) {
    fun normalized(): DashboardLayout {
        // Retain the user's ordering. In particular, do not rebuild this list
        // from enum order after a drag-and-drop reorder has been persisted.
        // Keep the first occurrence of an accidentally duplicated section and
        // append newly introduced section types in their default order.
        val supplied = sections.distinctBy { it.section }
        val suppliedSections = supplied.map { it.section }.toSet()
        return copy(
            sections = supplied + DashboardSection.entries
                .filterNot { it in suppliedSections }
                .map(::defaultSectionLayout),
            projectFilter = projectFilter?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    fun section(section: DashboardSection): DashboardSectionLayout =
        normalized().sections.first { it.section == section }

    companion object {
        val DEFAULT = DashboardLayout()

        private fun defaultSections(): List<DashboardSectionLayout> =
            DashboardSection.entries.map(::defaultSectionLayout)

        private fun defaultSectionLayout(section: DashboardSection): DashboardSectionLayout =
            DashboardSectionLayout(
                section = section,
                density = if (section == DashboardSection.QUICK_ACTIONS) {
                    DashboardDensity.COMPACT
                } else {
                    DashboardDensity.STANDARD
                }
            )

        fun preset(preset: DashboardPreset): DashboardLayout = when (preset) {
            DashboardPreset.DAILY_PLANNING -> DashboardLayout(
                sections = listOf(
                    DashboardSectionLayout(DashboardSection.TODAY, true, DashboardDensity.EXPANDED),
                    DashboardSectionLayout(DashboardSection.SUMMARY, true, DashboardDensity.COMPACT),
                    DashboardSectionLayout(DashboardSection.PROJECT_HEALTH, true, DashboardDensity.COMPACT),
                    DashboardSectionLayout(DashboardSection.SYNC, true, DashboardDensity.COMPACT),
                    DashboardSectionLayout(DashboardSection.QUICK_ACTIONS, true, DashboardDensity.COMPACT)
                ), timeRange = DashboardTimeRange.TODAY
            )
            DashboardPreset.PROJECT_REVIEW -> DashboardLayout(
                sections = listOf(
                    DashboardSectionLayout(DashboardSection.PROJECT_HEALTH, true, DashboardDensity.EXPANDED),
                    DashboardSectionLayout(DashboardSection.SUMMARY, true, DashboardDensity.STANDARD),
                    DashboardSectionLayout(DashboardSection.TODAY, false),
                    DashboardSectionLayout(DashboardSection.SYNC, true, DashboardDensity.COMPACT),
                    DashboardSectionLayout(DashboardSection.QUICK_ACTIONS, true, DashboardDensity.COMPACT)
                ), timeRange = DashboardTimeRange.MONTH
            )
            DashboardPreset.MINIMAL -> DashboardLayout(
                sections = listOf(
                    DashboardSectionLayout(DashboardSection.TODAY, true, DashboardDensity.COMPACT),
                    DashboardSectionLayout(DashboardSection.SUMMARY, true, DashboardDensity.COMPACT),
                    DashboardSectionLayout(DashboardSection.PROJECT_HEALTH, false),
                    DashboardSectionLayout(DashboardSection.SYNC, false),
                    DashboardSectionLayout(DashboardSection.QUICK_ACTIONS, true, DashboardDensity.COMPACT)
                ), timeRange = DashboardTimeRange.TODAY
            )
        }.normalized()
    }
}

/** Small, versioned delimiter format; fields are identifiers and base64-free strings are avoided. */
object DashboardLayoutSerializer {
    private const val VERSION = "v1"

    fun serialize(layout: DashboardLayout): String = layout.normalized().let { normalized ->
        buildList {
            add(VERSION)
            add("range=${normalized.timeRange.storageId}")
            add("project=${normalized.projectFilter.orEmpty().replace("%", "%25").replace("|", "%7C").replace("=", "%3D")}")
            normalized.sections.forEach { section ->
                add("${section.section.storageId}:${if (section.visible) 1 else 0}:${section.density.storageId}")
            }
        }.joinToString("|")
    }

    fun deserialize(value: String): DashboardLayout? {
        val pieces = value.split("|")
        if (pieces.firstOrNull() != VERSION) return null
        val range = pieces.firstOrNull { it.startsWith("range=") }
            ?.removePrefix("range=")?.let(DashboardTimeRange::fromStorage) ?: return null
        val project = pieces.firstOrNull { it.startsWith("project=") }?.removePrefix("project=")
            ?.replace("%3D", "=")?.replace("%7C", "|")?.replace("%25", "%")?.ifBlank { null }
        val sections = pieces.drop(1).mapNotNull { piece ->
            val values = piece.split(":")
            if (values.size != 3) null else DashboardSection.fromStorage(values[0])?.let {
                DashboardSectionLayout(it, values[1] == "1", DashboardDensity.fromStorage(values[2]) ?: return null)
            }
        }
        return DashboardLayout(sections, project, range).normalized()
    }

    /** Normalized key input shared conceptually with the DataStore implementation. */
    fun normalizedVaultPath(path: String): String = runCatching { File(path).canonicalPath }
        .getOrDefault(File(path).absoluteFile.normalize().path).trimEnd(File.separatorChar)
}

data class DashboardSummary(
    val openTasks: Int = 0,
    val dueToday: Int = 0,
    val overdue: Int = 0,
    val completedInRange: Int = 0,
    val remainingEffort: Int = 0
) {
    /** Compatibility-friendly name for the selected-range completion count. */
    val completed: Int get() = completedInRange
}

enum class ProjectRisk(val reason: String) {
    OVERDUE("Has overdue work"),
    NEAR_TARGET_WITH_REMAINING_EFFORT("Target date is near and work remains"),
    NO_COMPLETION_IN_RANGE("No work completed in the selected time range"),
    NO_NEXT_ACTION("No next action is defined"),
    ON_HOLD_DUE_WORK("On hold with due work")
}

data class DashboardProjectHealth(
    val project: Project,
    val openTasks: Int,
    val overdueTasks: Int,
    val remainingEffort: Int,
    val nextAction: TodoItem?,
    val risks: Set<ProjectRisk>
)

object DashboardCalculator {
    /**
     * Incomplete work requiring attention today: overdue work first, followed
     * by work due today. Tasks without a due date are intentionally excluded.
     */
    fun todayOpenTasks(todos: List<TodoItem>, today: LocalDate): List<TodoItem> =
        todos.asSequence()
            .filter { !it.isCompleted && it.dueDate?.let { due -> !due.isAfter(today) } == true }
            .sortedWith(compareBy<TodoItem> { it.dueDate }.thenBy { it.title }.thenBy { it.id })
            .toList()

    fun summary(todos: List<TodoItem>, today: LocalDate, range: DashboardTimeRange): DashboardSummary {
        val filtered = todos
        return DashboardSummary(
            openTasks = filtered.count { !it.isCompleted },
            dueToday = filtered.count { !it.isCompleted && it.dueDate == today },
            overdue = filtered.count { !it.isCompleted && it.dueDate?.isBefore(today) == true },
            completedInRange = filtered.count { it.isCompleted && !it.updatedAt.toLocalDate().isBefore(range.start(today)) },
            remainingEffort = filtered.filterNot { it.isCompleted }.sumOf { it.loe }
        )
    }

    fun projectHealth(projects: List<Project>, todos: List<TodoItem>, today: LocalDate, range: DashboardTimeRange): List<DashboardProjectHealth> =
        projects.filter { it.status == ProjectStatus.ACTIVE || it.status == ProjectStatus.ON_HOLD }.map { project ->
            val projectTodos = todos.filter { it.project == project.path || it.project.startsWith("${project.path}/") }
            val open = projectTodos.filterNot { it.isCompleted }
            val next = open.sortedWith(compareBy<TodoItem> { it.dueDate == null }.thenBy { it.dueDate }.thenBy { it.title }).firstOrNull()
            val completedInRange = projectTodos.any { it.isCompleted && !it.updatedAt.toLocalDate().isBefore(range.start(today)) }
            val risks = buildSet {
                if (open.any { it.dueDate?.isBefore(today) == true }) add(ProjectRisk.OVERDUE)
                if (project.targetDate != null && !project.targetDate.isBefore(today) &&
                    !project.targetDate.isAfter(today.plusDays(7)) && open.isNotEmpty()
                ) add(ProjectRisk.NEAR_TARGET_WITH_REMAINING_EFFORT)
                if (!completedInRange) add(ProjectRisk.NO_COMPLETION_IN_RANGE)
                if (next == null) add(ProjectRisk.NO_NEXT_ACTION)
                if (project.status == ProjectStatus.ON_HOLD && open.any { it.dueDate?.isBefore(today.plusDays(1)) == true }) add(ProjectRisk.ON_HOLD_DUE_WORK)
            }
            DashboardProjectHealth(
                project = project,
                openTasks = open.size,
                overdueTasks = open.count { it.dueDate?.isBefore(today) == true },
                remainingEffort = open.sumOf { it.loe },
                nextAction = next,
                risks = risks
            )
        }.sortedBy { it.project.name.lowercase() }
}
