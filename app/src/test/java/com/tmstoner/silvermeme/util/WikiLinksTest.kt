package com.tmstoner.silvermeme.util

import com.tmstoner.silvermeme.util.WikiLinks.Link
import com.tmstoner.silvermeme.util.WikiLinks.OpenLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikiLinksTest {

    @Test
    fun `find parses plain heading alias and embed links`() {
        val text = "See [[Meeting notes]], [[Plan#Goals]], [[Budget 2026|budget]] and ![[Diagram]]."
        assertEquals(
            listOf(
                Link("Meeting notes"),
                Link("Plan", heading = "Goals"),
                Link("Budget 2026", alias = "budget"),
                Link("Diagram")
            ),
            WikiLinks.find(text)
        )
    }

    @Test
    fun `find ignores unfinished blank and multi-line links`() {
        assertEquals(emptyList<Link>(), WikiLinks.find("[[open  [[]]  [[ ]] [[a\nb]] [single]"))
    }

    @Test
    fun `targets are distinct ignoring case and keep first spelling`() {
        assertEquals(listOf("Plan", "Other"), WikiLinks.targets("[[Plan]] [[plan|again]] [[Other]] [[PLAN#x]]"))
    }

    @Test
    fun `openLinkAt finds the query being typed`() {
        val text = "Call about [[Meet"
        assertEquals(OpenLink(start = 11, query = "Meet"), WikiLinks.openLinkAt(text, text.length))
        assertEquals(OpenLink(start = 0, query = ""), WikiLinks.openLinkAt("[[", 2))
    }

    @Test
    fun `openLinkAt is null outside an unfinished link`() {
        assertNull(WikiLinks.openLinkAt("no link here", 5))
        assertNull(WikiLinks.openLinkAt("[[Done]] after", 14))
        assertNull(WikiLinks.openLinkAt("[[Plan#Go", 9))
        assertNull(WikiLinks.openLinkAt("[[Plan|al", 9))
        assertNull(WikiLinks.openLinkAt("[[Plan\nnext", 11))
        assertNull(WikiLinks.openLinkAt("[", 1))
    }

    @Test
    fun `suggest ranks prefix matches before contains and caps the list`() {
        val names = listOf("Project plan", "Plan", "plan", "Meal plan", "Planning", "Other", "Airplane")
        assertEquals(listOf("Plan", "Planning", "Airplane", "Meal plan", "Project plan"), WikiLinks.suggest("pla", names))
        assertEquals(2, WikiLinks.suggest("", names, limit = 2).size)
    }

    @Test
    fun `complete replaces the partial link and places the cursor after it`() {
        val text = "Call about [[Mee today"
        val open = WikiLinks.openLinkAt(text, 16)!!
        val (newText, cursor) = WikiLinks.complete(text, 16, open, "Meeting notes")
        assertEquals("Call about [[Meeting notes]] today", newText)
        assertEquals("Call about [[Meeting notes]]".length, cursor)
    }

    @Test
    fun `complete reuses auto-closed brackets`() {
        val text = "[[Me]]"
        val (newText, cursor) = WikiLinks.complete(text, 4, OpenLink(0, "Me"), "Meeting")
        assertEquals("[[Meeting]]", newText)
        assertEquals(11, cursor)
    }

    @Test
    fun `obsidianUri encodes vault and file`() {
        assertEquals(
            "obsidian://open?vault=My%20Vault&file=Notes%2FPlan%20%26%20goals",
            WikiLinks.obsidianUri("My Vault", "Notes/Plan & goals")
        )
    }
}
