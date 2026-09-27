package com.tmstoner.silvermeme

import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.storage.MarkdownFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectFileOperationsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val manager get() = MarkdownFileManager(temporaryFolder.root)

    @Test
    fun `save and rename keep markdown under nested project folder`() {
        val first = manager.saveTodo(
            TodoItem(
                id = "task-1",
                title = "Initial title",
                content = "Keep this note",
                filePath = "Tasks/Work/Planning/Initial title.md"
            )
        )
        val originalFile = File(temporaryFolder.root, first.filePath)
        assertTrue(originalFile.isFile)

        val renamed = manager.saveTodo(
            first.copy(title = "Updated title"),
            previousFilePath = first.filePath
        )

        assertEquals("Tasks/Work/Planning/Updated title.md", renamed.filePath)
        assertFalse("A title rename should remove its stale backing file", originalFile.exists())
        val savedFile = File(temporaryFolder.root, renamed.filePath)
        assertTrue(savedFile.isFile)
        val parsed = manager.parseMarkdownFile(savedFile)!!
        assertEquals("task-1", parsed.id)
        assertEquals("Keep this note", parsed.content)
        assertEquals("Work/Planning", parsed.project)
    }

    @Test
    fun `trash and restore preserve nested project path and task content`() {
        val saved = manager.saveTodo(
            TodoItem(
                id = "restore-me",
                title = "Quarterly report",
                content = "Do not lose this",
                filePath = "Tasks/Work/Reports/Quarterly report.md"
            )
        )

        val trashed = manager.trashTodo(saved)
        assertEquals(
            "Tasks/.trash/Work/Reports/Quarterly report.md",
            trashed.filePath
        )
        assertFalse(File(temporaryFolder.root, saved.filePath).exists())
        assertEquals(1, manager.getTrashedTodos().size)

        val restored = manager.restoreTodo(trashed)
        assertEquals(saved.filePath, restored.filePath)
        val parsed = manager.parseMarkdownFile(File(temporaryFolder.root, restored.filePath))!!
        assertEquals("restore-me", parsed.id)
        assertEquals("Do not lose this", parsed.content)
        assertTrue(manager.getTrashedTodos().isEmpty())
    }

    @Test
    fun `new task with an occupied filename gets a distinct path without overwriting existing task`() {
        val existing = manager.saveTodo(
            TodoItem(id = "first", title = "Same title", content = "Original")
        )

        val second = manager.saveTodo(
            TodoItem(id = "second", title = "Same title", content = "Second")
        )

        assertEquals("Tasks/Same title.md", existing.filePath)
        assertTrue("Filename collision should choose another path", second.filePath != existing.filePath)
        assertEquals(
            "Original",
            manager.parseMarkdownFile(File(temporaryFolder.root, existing.filePath))!!.content
        )
        assertEquals(
            "Second",
            manager.parseMarkdownFile(File(temporaryFolder.root, second.filePath))!!.content
        )
    }
}
