package com.tmstoner.silvermeme

import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.storage.MarkdownFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class ReminderPersistenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `reminder settings persist and legacy frontmatter keeps midnight default`() {
        val manager = MarkdownFileManager(temporaryFolder.root)
        val custom = TodoItem(
            id = "custom",
            title = "Custom time",
            dueDate = LocalDate.of(2026, 10, 2),
            reminderEnabled = true,
            reminderTime = LocalTime.of(8, 45)
        )
        val saved = manager.saveTodo(custom)
        val parsed = manager.parseMarkdownFile(File(temporaryFolder.root, saved.filePath))!!

        assertEquals(true, parsed.reminderEnabled)
        assertEquals(LocalTime.of(8, 45), parsed.reminderTime)

        val disabled = manager.saveTodo(custom.copy(title = "No reminder", reminderEnabled = false))
        val parsedDisabled = manager.parseMarkdownFile(File(temporaryFolder.root, disabled.filePath))!!
        assertEquals(false, parsedDisabled.reminderEnabled)
        assertEquals(LocalTime.of(8, 45), parsedDisabled.reminderTime)

        val legacyFile = File(temporaryFolder.root, "Tasks/legacy.md").apply {
            parentFile?.mkdirs()
            writeText("---\ntitle: Legacy\ndue: 2026-10-02\n---\n")
        }
        val legacy = manager.parseMarkdownFile(legacyFile)!!
        assertTrue(legacy.reminderEnabled)
        assertEquals(null, legacy.reminderTime)
    }
}
