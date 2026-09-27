package com.tmstoner.silvermeme.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoSearchFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchInputIsEditableAndClearActionResetsItsQuery() {
        val query = mutableStateOf("")
        composeRule.setContent {
            MaterialTheme {
                TodoSearchField(
                    query = query.value,
                    onQueryChange = { query.value = it },
                    onClear = { query.value = "" }
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("silver")
        composeRule.runOnIdle { assertEquals("silver", query.value) }
        composeRule.onNodeWithText("silver").assertTextEquals("silver")
        composeRule.onNodeWithContentDescription("Clear search").performClick()
        composeRule.runOnIdle { assertEquals("", query.value) }
    }
}
