package com.tmstoner.silvermeme

import com.tmstoner.silvermeme.data.model.Project
import com.tmstoner.silvermeme.data.model.ProjectStatus
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.storage.MarkdownFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class ProjectPersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val manager get() = MarkdownFileManager(temporaryFolder.root)

    @Test
    fun `project markdown round trip preserves metadata and description`() {
        val saved = manager.saveProject(
            Project(
                id = "plan-id", name = "Planning", path = "Work/Planning",
                status = ProjectStatus.ON_HOLD, description = "## Notes\nKeep **this** markdown.",
                targetDate = LocalDate.of(2026, 10, 1), color = "#336699", icon = "🗓",
                tags = listOf("work", "q4"), loe = 8, recurrence = "weekly:MON",
                reminderEnabled = false
            )
        )

        val read = manager.getProject("plan-id")!!
        assertEquals(saved.id, read.id)
        assertEquals("Work/Planning", read.path)
        assertEquals(ProjectStatus.ON_HOLD, read.status)
        assertEquals(saved.description, read.description)
        assertEquals(saved.targetDate, read.targetDate)
        assertEquals(listOf("work", "q4"), read.tags)
        assertTrue(File(temporaryFolder.root, "Projects/Work/Planning.md").isFile)
    }

    @Test
    fun `move rename preserves project ids nested projects and task markdown`() {
        manager.saveProject(Project("work", "Work", "Work"))
        manager.saveProject(Project("plan", "Planning", "Work/Planning"))
        val rootTask = manager.saveTodo(TodoItem("root-task", "Root", "root body", filePath = "Tasks/Work/Root.md"))
        manager.saveTodo(TodoItem("child-task", "Child", "child body", filePath = "Tasks/Work/Planning/Child.md"))

        val moved = manager.moveProject("work", "Personal", "Home")

        assertEquals("Personal/Home", moved.path)
        assertEquals("plan", manager.getProject("plan")!!.id)
        assertEquals("Personal/Home/Planning", manager.getProject("plan")!!.path)
        assertFalse(File(temporaryFolder.root, rootTask.filePath).exists())
        val movedTask = manager.getTodosForProject("Personal/Home").first { it.id == "root-task" }
        assertEquals("root body", movedTask.content)
        assertEquals("Tasks/Personal/Home/Root.md", movedTask.filePath)
        val movedChild = manager.getTodosForProject("Personal/Home").first { it.id == "child-task" }
        assertEquals("child body", movedChild.content)
        assertEquals("Tasks/Personal/Home/Planning/Child.md", movedChild.filePath)
    }

    @Test
    fun `archive and restore move task folder without changing lifecycle status`() {
        manager.saveProject(Project("work", "Work", "Work", status = ProjectStatus.COMPLETED))
        manager.saveTodo(TodoItem("task", "Task", "body", filePath = "Tasks/Work/Task.md"))

        val archived = manager.archiveProject("work")
        assertTrue(archived.isArchived)
        assertEquals(ProjectStatus.COMPLETED, archived.status)
        assertTrue(File(temporaryFolder.root, "Tasks/.archive/Work/Task.md").isFile)
        assertTrue(manager.getAllTodos().isEmpty())

        val restored = manager.restoreProject("work")
        assertFalse(restored.isArchived)
        assertEquals(ProjectStatus.COMPLETED, restored.status)
        val restoredTask = manager.getTodosForProject("Work").single()
        assertEquals("task", restoredTask.id)
        assertEquals("body", restoredTask.content)
        assertEquals("Tasks/Work/Task.md", restoredTask.filePath)
    }

    @Test
    fun `bulk task move preserves identifiers and content`() {
        val first = manager.saveTodo(TodoItem("one", "One", "first", filePath = "Tasks/From/One.md"))
        val second = manager.saveTodo(TodoItem("two", "Two", "second", filePath = "Tasks/From/Two.md"))

        val moved = manager.moveTodosToProject(listOf(first, second), "To/Nested")

        assertEquals(listOf("one", "two"), moved.map { it.id })
        assertEquals(listOf("first", "second"), manager.getTodosForProject("To").map { it.content }.sorted())
        assertFalse(File(temporaryFolder.root, "Tasks/From/One.md").exists())
        assertTrue(File(temporaryFolder.root, "Tasks/To/Nested/One.md").isFile)
    }

    @Test
    fun `project metrics exclude trashed tasks and include active nested tasks`() {
        manager.saveTodo(
            TodoItem(
                id = "open", title = "Open", dueDate = LocalDate.of(2026, 9, 1),
                loe = 5, filePath = "Tasks/Work/Open.md"
            )
        )
        manager.saveTodo(
            TodoItem(
                id = "done", title = "Done", isCompleted = true, loe = 3,
                filePath = "Tasks/Work/Nested/Done.md"
            )
        )
        val trashed = manager.saveTodo(
            TodoItem(
                id = "trash", title = "Trash", dueDate = LocalDate.of(2026, 8, 1),
                loe = 13, filePath = "Tasks/Work/Trash.md"
            )
        )
        manager.trashTodo(trashed)

        val metrics = manager.getProjectMetrics("Work", LocalDate.of(2026, 9, 27))

        assertEquals(1, metrics.openCount)
        assertEquals(1, metrics.completedCount)
        assertEquals(1, metrics.overdueCount)
        assertEquals(5, metrics.remainingEffort)
        assertEquals(3, metrics.completedEffort)
        assertEquals(LocalDate.of(2026, 9, 1), metrics.nearestDue)
        assertEquals(3f / 8f, metrics.progress, 0.0001f)
    }
}
