package com.tmstoner.silvermeme

import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.storage.MarkdownFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Unit tests for [MarkdownFileManager].
 *
 * Covers:
 *  - Parsing frontmatter fields
 *  - Serialisation round-trip (write then read)
 *  - Save / delete lifecycle
 *  - Edge cases (missing fields, malformed files, special chars in title)
 */
class MarkdownFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var manager: MarkdownFileManager

    @Before
    fun setUp() {
        manager = MarkdownFileManager(tempFolder.root)
    }

    // ── parseFrontmatter ──────────────────────────────────────────────────────

    @Test
    fun `parseFrontmatter extracts scalar values`() {
        val content = """
            ---
            id: abc-123
            title: Buy milk
            status: open
            priority: high
            due: 2024-03-15
            location: Superstore
            created: 2024-03-01T09:00:00
            updated: 2024-03-01T09:00:00
            ---
        """.trimIndent()

        val fm = manager.parseFrontmatter(content)
        assertEquals("abc-123", fm["id"])
        assertEquals("Buy milk", fm["title"])
        assertEquals("open", fm["status"])
        assertEquals("high", fm["priority"])
        assertEquals("2024-03-15", fm["due"])
        assertEquals("Superstore", fm["location"])
    }

    @Test
    fun `parseFrontmatter extracts block sequence tags`() {
        val content = """
            ---
            title: Errand
            tags:
              - shopping
              - errands
            ---
        """.trimIndent()

        val fm = manager.parseFrontmatter(content)
        assertEquals("shopping,errands", fm["tags"])
    }

    @Test
    fun `parseFrontmatter returns empty map when no frontmatter`() {
        val content = "Just plain text without any frontmatter."
        val fm = manager.parseFrontmatter(content)
        assertTrue(fm.isEmpty())
    }

    @Test
    fun `parseFrontmatter handles unclosed delimiter gracefully`() {
        val content = """
            ---
            title: Broken
            priority: low
        """.trimIndent()

        val fm = manager.parseFrontmatter(content)
        assertTrue(fm.isEmpty())
    }

    // ── parseBody ─────────────────────────────────────────────────────────────

    @Test
    fun `parseBody returns content after closing delimiter`() {
        val content = """
            ---
            title: Test
            ---

            ## Notes

            Hello world
        """.trimIndent()

        val body = manager.parseBody(content)
        assertTrue(body.contains("## Notes"))
        assertTrue(body.contains("Hello world"))
    }

    @Test
    fun `parseBody returns full content when no frontmatter`() {
        val content = "No frontmatter here."
        assertEquals(content, manager.parseBody(content))
    }

    // ── serializeToMarkdown ───────────────────────────────────────────────────

    @Test
    fun `serializeToMarkdown produces valid frontmatter`() {
        val todo = sampleTodo()
        val md = manager.serializeToMarkdown(todo)

        assertTrue(md.startsWith("---"))
        assertTrue(md.contains("title: Buy groceries"))
        assertTrue(md.contains("priority: high"))
        assertTrue(md.contains("due: 2024-03-15"))
        assertTrue(md.contains("location: Superstore"))
        assertTrue(md.contains("  - shopping"))
        assertTrue(md.contains("  - errands"))
        assertTrue(md.contains("status: open"))
    }

    @Test
    fun `serializeToMarkdown marks completed items as done`() {
        val todo = sampleTodo().copy(isCompleted = true)
        val md = manager.serializeToMarkdown(todo)
        assertTrue(md.contains("status: done"))
    }

    @Test
    fun `serializeToMarkdown omits optional fields when absent`() {
        val todo = sampleTodo().copy(location = null, tags = emptyList(), dueDate = null)
        val md = manager.serializeToMarkdown(todo)
        assertTrue(!md.contains("location:"))
        assertTrue(!md.contains("tags:"))
        assertTrue(!md.contains("due:"))
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `round-trip preserves all fields`() {
        val original = sampleTodo()
        val md = manager.serializeToMarkdown(original)
        val parsed = manager.parseMarkdownFile(createTempMd(md, "buy-groceries"))!!

        assertTodoRoundTrip(original, parsed)
        assertTrue(parsed.content.contains("Don't forget the reusable bags"))
    }

    @Test
    fun `round-trip works for completed item`() {
        val todo = sampleTodo().copy(isCompleted = true)
        val md   = manager.serializeToMarkdown(todo)
        val parsed = manager.parseMarkdownFile(createTempMd(md, "done-item"))!!
        assertTrue(parsed.isCompleted)
    }

    // ── Save / delete ─────────────────────────────────────────────────────────

    @Test
    fun `saveTodo creates file in Tasks folder`() {
        val todo  = sampleTodo()
        val saved = manager.saveTodo(todo)

        val file = java.io.File(tempFolder.root, saved.filePath)
        assertTrue(file.exists())
        assertTrue(file.readText().contains("Buy groceries"))
    }

    @Test
    fun `getAllTodos returns saved item`() {
        manager.saveTodo(sampleTodo())
        val todos = manager.getAllTodos()
        assertEquals(1, todos.size)
        assertEquals("Buy groceries", todos[0].title)
    }

    @Test
    fun `deleteTodo removes the file`() {
        val saved = manager.saveTodo(sampleTodo())
        manager.deleteTodo(saved)
        val file = java.io.File(tempFolder.root, saved.filePath)
        assertTrue(!file.exists())
    }

    @Test
    fun `getAllTodos returns empty list when folder does not exist`() {
        val manager2 = MarkdownFileManager(java.io.File(tempFolder.root, "nonexistent"))
        assertTrue(manager2.getAllTodos().isEmpty())
    }

    // ── Filename sanitisation ─────────────────────────────────────────────────

    @Test
    fun `sanitizeFilename strips illegal characters`() {
        val name = manager.sanitizeFilename("Task: buy milk / eggs <today>")
        assertTrue(!name.contains(':'))
        assertTrue(!name.contains('/'))
        assertTrue(!name.contains('<'))
        assertTrue(!name.contains('>'))
    }

    @Test
    fun `sanitizeFilename returns untitled for blank input`() {
        assertEquals("untitled", manager.sanitizeFilename("   "))
    }

    // ── Priority parsing ──────────────────────────────────────────────────────

    @Test
    fun `Priority fromString handles known values`() {
        assertEquals(Priority.LOW,    Priority.fromString("low"))
        assertEquals(Priority.MEDIUM, Priority.fromString("medium"))
        assertEquals(Priority.MEDIUM, Priority.fromString("normal"))
        assertEquals(Priority.HIGH,   Priority.fromString("high"))
        assertEquals(Priority.URGENT, Priority.fromString("urgent"))
        assertEquals(Priority.URGENT, Priority.fromString("critical"))
    }

    @Test
    fun `Priority fromString defaults to MEDIUM for unknown`() {
        assertEquals(Priority.MEDIUM, Priority.fromString(null))
        assertEquals(Priority.MEDIUM, Priority.fromString(""))
        assertEquals(Priority.MEDIUM, Priority.fromString("unknown"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sampleTodo() = TodoItem(
        id          = "test-id-001",
        title       = "Buy groceries",
        content     = "Don't forget the reusable bags.",
        dueDate     = LocalDate.of(2024, 3, 15),
        priority    = Priority.HIGH,
        location    = "Superstore",
        tags        = listOf("shopping", "errands"),
        isCompleted = false,
        createdAt   = LocalDateTime.of(2024, 3, 1, 9, 0)
    )

    private fun createTempMd(content: String, name: String): java.io.File {
        val tasksDir = java.io.File(tempFolder.root, MarkdownFileManager.TASKS_FOLDER)
        tasksDir.mkdirs()
        return java.io.File(tasksDir, "$name.md").also { it.writeText(content) }
    }
}
