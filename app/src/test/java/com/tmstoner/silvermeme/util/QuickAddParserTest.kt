package com.tmstoner.silvermeme.util

import com.tmstoner.silvermeme.data.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class QuickAddParserTest {

    // A Tuesday, so weekday maths is easy to check.
    private val today = LocalDate.of(2026, 10, 6)

    @Test
    fun `parses title date priority and tags`() {
        val r = QuickAddParser.parse("Buy milk tomorrow !high #errand #Home", today)
        assertEquals("Buy milk", r.title)
        assertEquals(today.plusDays(1), r.dueDate)
        assertEquals(Priority.HIGH, r.priority)
        assertEquals(listOf("errand", "home"), r.tags)
    }

    @Test
    fun `plain text is all title`() {
        val r = QuickAddParser.parse("  Call the plumber  ", today)
        assertEquals("Call the plumber", r.title)
        assertNull(r.dueDate)
        assertNull(r.priority)
        assertEquals(emptyList<String>(), r.tags)
    }

    @Test
    fun `weekday resolves to next occurrence including today`() {
        assertEquals(today, QuickAddParser.parse("Standup tuesday", today).dueDate)
        assertEquals(LocalDate.of(2026, 10, 9), QuickAddParser.parse("Report fri", today).dueDate)
        assertEquals(LocalDate.of(2026, 10, 12), QuickAddParser.parse("Plan Monday", today).dueDate)
    }

    @Test
    fun `ambiguous short words stay in the title`() {
        val r = QuickAddParser.parse("Buy sun cream", today)
        assertEquals("Buy sun cream", r.title)
        assertNull(r.dueDate)
    }

    @Test
    fun `next week and iso dates`() {
        assertEquals(today.plusWeeks(1), QuickAddParser.parse("Review next week", today).dueDate)
        val iso = QuickAddParser.parse("Taxes 2027-04-15", today)
        assertEquals("Taxes", iso.title)
        assertEquals(LocalDate.of(2027, 4, 15), iso.dueDate)
    }

    @Test
    fun `invalid iso date and unknown bang stay in title`() {
        val r = QuickAddParser.parse("Fix 2026-13-40 !soon", today)
        assertEquals("Fix 2026-13-40 !soon", r.title)
        assertNull(r.dueDate)
        assertNull(r.priority)
    }

    @Test
    fun `last date wins and tokens only input is invalid`() {
        assertEquals(today.plusDays(1), QuickAddParser.parse("X today tomorrow", today).dueDate)
        assertFalse(QuickAddParser.parse("tomorrow !high #x", today).isValid)
    }

    @Test
    fun `toTodoItem places task in project folder and defaults priority`() {
        val todo = QuickAddParser.parse("Write: report?", today).toTodoItem(project = "Work")
        assertEquals("Write: report?", todo.title)
        assertEquals(Priority.MEDIUM, todo.priority)
        assertEquals("Work", todo.project)
        assertEquals("Tasks/Work/Write_ report_.md", todo.filePath)
        assertEquals("Tasks/Write_ report_.md", QuickAddParser.parse("Write: report?", today).toTodoItem().filePath)
    }
}
