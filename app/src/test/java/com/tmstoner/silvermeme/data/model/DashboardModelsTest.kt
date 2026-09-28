package com.tmstoner.silvermeme.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.tmstoner.silvermeme.viewmodel.DashboardSyncStatus
import java.time.LocalDate
import java.time.LocalDateTime

class DashboardModelsTest {
    @Test
    fun layoutSerializationRoundTripsOrderDensityAndProject() {
        val layout = DashboardLayout(
            sections = listOf(
                DashboardSectionLayout(DashboardSection.PROJECT_HEALTH, false, DashboardDensity.EXPANDED),
                DashboardSectionLayout(DashboardSection.TODAY, true, DashboardDensity.COMPACT)
            ),
            projectFilter = "Work|Planning",
            timeRange = DashboardTimeRange.MONTH
        )

        val restored = DashboardLayoutSerializer.deserialize(DashboardLayoutSerializer.serialize(layout))

        assertEquals(layout.normalized(), restored)
        assertEquals(
            listOf(
                DashboardSection.PROJECT_HEALTH,
                DashboardSection.TODAY,
                DashboardSection.SUMMARY,
                DashboardSection.SYNC,
                DashboardSection.QUICK_ACTIONS
            ),
            restored!!.sections.map { it.section }
        )
    }

    @Test
    fun quickActionsSerializationPreservesItsReorderedVisibilityAndDensity() {
        val layout = DashboardLayout(
            sections = listOf(
                DashboardSectionLayout(DashboardSection.QUICK_ACTIONS, false, DashboardDensity.EXPANDED),
                DashboardSectionLayout(DashboardSection.TODAY, true, DashboardDensity.COMPACT)
            )
        )

        val restored = DashboardLayoutSerializer.deserialize(DashboardLayoutSerializer.serialize(layout))!!

        assertEquals(DashboardSection.QUICK_ACTIONS, restored.sections.first().section)
        assertFalse(restored.section(DashboardSection.QUICK_ACTIONS).visible)
        assertEquals(DashboardDensity.EXPANDED, restored.section(DashboardSection.QUICK_ACTIONS).density)
    }

    @Test
    fun legacyV1LayoutAppendsDefaultQuickActions() {
        val legacy = "v1|range=week|project=|today:1:standard|summary:1:standard|" +
            "project_health:1:standard|sync:1:standard"

        val restored = DashboardLayoutSerializer.deserialize(legacy)!!

        assertEquals(DashboardSection.QUICK_ACTIONS, restored.sections.last().section)
        assertTrue(restored.section(DashboardSection.QUICK_ACTIONS).visible)
        assertEquals(DashboardDensity.COMPACT, restored.section(DashboardSection.QUICK_ACTIONS).density)
    }

    @Test
    fun layoutSerializationEscapesAllDelimiterCharactersInProjectFilter() {
        val layout = DashboardLayout(
            projectFilter = "Work|Planning=Q3%review",
            timeRange = DashboardTimeRange.TODAY
        )

        val restored = DashboardLayoutSerializer.deserialize(
            DashboardLayoutSerializer.serialize(layout)
        )

        assertEquals(layout.normalized(), restored)
    }

    @Test
    fun malformedLayoutIsRejectedInsteadOfPartiallyApplyingIt() {
        assertEquals(null, DashboardLayoutSerializer.deserialize("v2|range=week|project="))
        assertEquals(null, DashboardLayoutSerializer.deserialize("v1|range=year|project="))
        assertEquals(
            null,
            DashboardLayoutSerializer.deserialize(
                "v1|range=week|project=|today:1:not-a-density"
            )
        )
    }

    @Test
    fun normalizeKeepsFirstDuplicateAndAddsMissingSectionsInDefaultOrder() {
        val layout = DashboardLayout(
            sections = listOf(
                DashboardSectionLayout(DashboardSection.TODAY, false, DashboardDensity.EXPANDED),
                DashboardSectionLayout(DashboardSection.TODAY, true, DashboardDensity.COMPACT)
            )
        ).normalized()

        assertEquals(
            listOf(
                DashboardSection.TODAY,
                DashboardSection.SUMMARY,
                DashboardSection.PROJECT_HEALTH,
                DashboardSection.SYNC,
                DashboardSection.QUICK_ACTIONS
            ),
            layout.sections.map { it.section }
        )
        assertFalse(layout.section(DashboardSection.TODAY).visible)
        assertEquals(DashboardDensity.EXPANDED, layout.section(DashboardSection.TODAY).density)
    }

    @Test
    fun configuredRemoteSyncIsStaleAfterConfiguredThreshold() {
        val now = 1_000_000L
        val status = DashboardSyncStatus(
            remoteConfigured = true,
            lastSuccessfulSyncTime = now - 1_000L,
            staleThresholdMillis = 1_000L
        )

        assertTrue(status.isStaleAt(now))
        assertFalse(status.isStaleAt(now - 1L))
        assertFalse(status.copy(remoteConfigured = false).isStaleAt(now))
    }

    @Test
    fun todayOpenTasksIncludesOverdueBeforeDueToday() {
        val today = LocalDate.of(2026, 9, 27)
        val overdue = TodoItem(id = "overdue", title = "Overdue", dueDate = today.minusDays(1))
        val dueToday = TodoItem(id = "today", title = "Due today", dueDate = today)
        val completed = TodoItem(id = "completed", title = "Completed", dueDate = today, isCompleted = true)
        val future = TodoItem(id = "future", title = "Future", dueDate = today.plusDays(1))

        assertEquals(
            listOf(overdue, dueToday),
            DashboardCalculator.todayOpenTasks(listOf(dueToday, completed, future, overdue), today)
        )
    }

    @Test
    fun projectHealthReportsFixedRisksFromLocalData() {
        val today = LocalDate.of(2026, 9, 27)
        val project = Project("p1", "Work", "Work", targetDate = today.plusDays(2))
        val todo = TodoItem(
            id = "t1",
            title = "Late task",
            filePath = "Tasks/Work/Late task.md",
            dueDate = today.minusDays(1),
            loe = 5
        )

        val health = DashboardCalculator.projectHealth(
            listOf(project), listOf(todo), today, DashboardTimeRange.WEEK
        ).single()

        assertTrue(health.risks.contains(ProjectRisk.OVERDUE))
        assertTrue(health.risks.contains(ProjectRisk.NEAR_TARGET_WITH_REMAINING_EFFORT))
        assertTrue(health.risks.contains(ProjectRisk.NO_COMPLETION_IN_RANGE))
        assertEquals(5, health.remainingEffort)
    }

    @Test
    fun projectHealthIncludesNestedTasksAndExplainsOnHoldDueWork() {
        val today = LocalDate.of(2026, 9, 27)
        val project = Project(
            id = "p1",
            name = "Work",
            path = "Work",
            status = ProjectStatus.ON_HOLD
        )
        val dueToday = TodoItem(
            id = "t1",
            title = "Blocked task",
            filePath = "Tasks/Work/Planning/Blocked task.md",
            dueDate = today,
            loe = 3
        )

        val health = DashboardCalculator.projectHealth(
            listOf(project),
            listOf(dueToday),
            today,
            DashboardTimeRange.WEEK
        ).single()

        assertEquals(1, health.openTasks)
        assertEquals(3, health.remainingEffort)
        assertEquals(dueToday, health.nextAction)
        assertTrue(health.risks.contains(ProjectRisk.ON_HOLD_DUE_WORK))
        assertTrue(health.risks.contains(ProjectRisk.NO_COMPLETION_IN_RANGE))
    }
}
