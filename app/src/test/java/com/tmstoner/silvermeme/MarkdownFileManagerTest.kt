package com.tmstoner.silvermeme

import com.tmstoner.silvermeme.data.model.ChecklistItem
import com.tmstoner.silvermeme.data.model.Priority
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.model.cleaned
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

    // ── Trash ─────────────────────────────────────────────────────────────────

    @Test
    fun `trashed items are not returned by getAllTodos`() {
        val saved = manager.saveTodo(sampleTodo())
        manager.trashTodo(saved)
        assertTrue(manager.getAllTodos().isEmpty())
        assertEquals(1, manager.getTrashedTodos().size)
    }

    @Test
    fun `trashed project items are not returned by getAllTodos`() {
        val saved = manager.saveTodo(sampleTodo().copy(filePath = "Tasks/Work/Buy groceries.md"))
        manager.trashTodo(saved)
        assertTrue(manager.getAllTodos().isEmpty())
        assertEquals(1, manager.getTrashedTodos().size)
    }

    @Test
    fun `restoreTodo brings a trashed item back into getAllTodos`() {
        val trashed = manager.trashTodo(manager.saveTodo(sampleTodo()))
        manager.restoreTodo(trashed)
        assertEquals(listOf("Buy groceries"), manager.getAllTodos().map { it.title })
        assertTrue(manager.getTrashedTodos().isEmpty())
    }

    @Test
    fun `getAllTodos returns empty list when folder does not exist`() {
        val manager2 = MarkdownFileManager(java.io.File(tempFolder.root, "nonexistent"))
        assertTrue(manager2.getAllTodos().isEmpty())
    }

    // ── Filename sanitisation ─────────────────────────────────────────────────

    @Test
    fun `sanitizeFilename strips illegal characters`() {
        val name = MarkdownFileManager.sanitizeFilename("Task: buy milk / eggs <today>")
        assertTrue(!name.contains(':'))
        assertTrue(!name.contains('/'))
        assertTrue(!name.contains('<'))
        assertTrue(!name.contains('>'))
    }

    @Test
    fun `sanitizeFilename returns untitled for blank input`() {
        assertEquals("untitled", MarkdownFileManager.sanitizeFilename("   "))
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

    // ── Checklist round-trip (Bug 2 regression guard) ────────────────────────

    @Test
    fun `cleaned checklist drops blank items so they are not saved`() {
        val edited = listOf(
            ChecklistItem("  Pack bags ", isDone = true),
            ChecklistItem("   "),
            ChecklistItem("Book taxi")
        )
        manager.saveTodo(sampleTodo().copy(checklist = edited.cleaned()))
        val reloaded = manager.getAllTodos().single()
        assertEquals(
            listOf(ChecklistItem("Pack bags", isDone = true), ChecklistItem("Book taxi")),
            reloaded.checklist
        )
    }

    @Test
    fun `checklist items survive a full save-parse-edit-save-parse round trip`() {
        val checklist = listOf(
            com.tmstoner.silvermeme.data.model.ChecklistItem("Buy milk", isDone = true),
            com.tmstoner.silvermeme.data.model.ChecklistItem("Buy eggs", isDone = false),
            com.tmstoner.silvermeme.data.model.ChecklistItem("Buy bread", isDone = false)
        )
        val original = sampleTodo().copy(checklist = checklist)

        // Step 1-3: save and parse back
        val saved1 = manager.saveTodo(original)
        val file1 = java.io.File(tempFolder.root, saved1.filePath)
        val parsed1 = manager.parseMarkdownFile(file1)!!

        // Step 4: all 3 items present with correct isDone
        assertEquals(3, parsed1.checklist.size)
        assertEquals("Buy milk", parsed1.checklist[0].text)
        assertTrue("First item should be done", parsed1.checklist[0].isDone)
        assertEquals("Buy eggs", parsed1.checklist[1].text)
        assertTrue("Second item should be undone", !parsed1.checklist[1].isDone)
        assertEquals("Buy bread", parsed1.checklist[2].text)
        assertTrue("Third item should be undone", !parsed1.checklist[2].isDone)

        // Step 5-6: simulate a title edit with the same checklist preserved
        val edited = parsed1.copy(title = "Buy groceries updated", checklist = parsed1.checklist)
        val saved2 = manager.saveTodo(edited, previousFilePath = parsed1.filePath)

        // Step 7: parse again and assert checklist is intact
        val file2 = java.io.File(tempFolder.root, saved2.filePath)
        val parsed2 = manager.parseMarkdownFile(file2)!!

        assertEquals(3, parsed2.checklist.size)
        assertEquals("Buy milk", parsed2.checklist[0].text)
        assertTrue("First item should still be done", parsed2.checklist[0].isDone)
        assertEquals("Buy eggs", parsed2.checklist[1].text)
        assertTrue("Second item should still be undone", !parsed2.checklist[1].isDone)
        assertEquals("Buy bread", parsed2.checklist[2].text)
        assertTrue("Third item should still be undone", !parsed2.checklist[2].isDone)
        assertEquals("Buy groceries updated", parsed2.title)
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
