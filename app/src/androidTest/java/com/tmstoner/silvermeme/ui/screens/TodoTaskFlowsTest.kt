package com.tmstoner.silvermeme.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.model.WidgetTodoSnapshot
import com.tmstoner.silvermeme.data.repository.GitRepository
import com.tmstoner.silvermeme.data.repository.TodoDataSource
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TodoTaskFlowsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createTaskSavesEnteredTitleAndNotes() {
        val source = FakeTodoDataSource()
        val viewModel = TodoViewModel(source)
        val navigatedBack = mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                TodoDetailScreen(viewModel = viewModel, onBack = { navigatedBack.value = true })
            }
        }

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("New task")
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("Task notes")
        composeRule.onNodeWithText("Create task").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { source.saved.size == 1 }
        composeRule.runOnIdle {
            val created = source.saved.single()
            assertEquals("New task", created.title)
            assertEquals("Task notes", created.content)
            assertEquals("Tasks/New task.md", created.filePath)
            assertTrue(navigatedBack.value)
        }
    }

    @Test
    fun editTaskSavesTitleChangeWithPreviousProjectPath() {
        val source = FakeTodoDataSource()
        val existing = TodoItem(
            id = "edit-me",
            title = "Old title",
            content = "Original notes",
            filePath = "Tasks/Work/Old title.md"
        )
        val viewModel = TodoViewModel(source)
        composeRule.setContent {
            MaterialTheme {
                TodoDetailScreen(viewModel = viewModel, existingTodo = existing, onBack = {})
            }
        }

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Renamed task")
        composeRule.onNodeWithText("Save task").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { source.saved.size == 1 }
        composeRule.runOnIdle {
            assertEquals("Renamed task", source.saved.single().title)
            assertEquals("edit-me", source.saved.single().id)
            assertEquals("Tasks/Work/Renamed task.md", source.saved.single().filePath)
            assertEquals("Tasks/Work/Old title.md", source.previousFilePaths.single())
        }
    }

    @Test
    fun projectFilterNarrowsTasksAndAccessibleSelectionMovesTaskToTrash() {
        val source = FakeTodoDataSource(
            initialTodos = mutableListOf(
                TodoItem(id = "work", title = "Work item", filePath = "Tasks/Work/Work item.md"),
                TodoItem(id = "home", title = "Home item", filePath = "Tasks/Home/Home item.md")
            )
        )
        val viewModel = TodoViewModel(source)
        composeRule.setContent {
            MaterialTheme {
                TodoListScreen(
                    viewModel = viewModel,
                    onAddTodo = {},
                    onEditTodo = {},
                    onOpenNavigationDrawer = {}
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.uiState.value.isLoading && viewModel.uiState.value.todos.size == 2
        }
        composeRule.onNodeWithContentDescription("Filter").performClick()
        composeRule.onNodeWithText("Work").performClick()
        assertTrue(composeRule.onAllNodes(hasText("Work item")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodes(hasText("Home item")).fetchSemanticsNodes().isEmpty())

        // The toolbar selection button is a non-gesture route to bulk actions.
        composeRule.onNodeWithContentDescription("Select tasks").performClick()
        composeRule.onNodeWithContentDescription("Move to trash").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { source.trashed.any { it.id == "work" } }
        composeRule.runOnIdle {
            assertTrue(viewModel.uiState.value.todos.none { it.id == "work" })
            assertEquals("Tasks/.trash/Work/Work item.md", source.trashed.single().filePath)
        }
    }

    @Test
    fun taskCompletionIsExposedAsAnAccessibleAction() {
        val task = TodoItem(id = "accessible", title = "Accessible task")
        val source = FakeTodoDataSource(mutableListOf(task))
        composeRule.setContent {
            MaterialTheme {
                TodoListScreen(
                    viewModel = TodoViewModel(source),
                    onAddTodo = {},
                    onEditTodo = {},
                    onOpenNavigationDrawer = {}
                )
            }
        }

        val completeAction = composeRule.onNodeWithContentDescription("Mark task complete")
        completeAction.performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            source.saved.any { it.id == "accessible" && it.isCompleted }
        }
        composeRule.runOnIdle {
            assertTrue(source.saved.last { it.id == "accessible" }.isCompleted)
        }
    }
}

private class FakeTodoDataSource(
    val initialTodos: MutableList<TodoItem> = mutableListOf()
) : TodoDataSource {
    override val automaticSyncEvents = MutableSharedFlow<GitRepository.GitResult>(extraBufferCapacity = 1)
    override val pendingSync = emptyFlow<Boolean>()
    val saved = mutableListOf<TodoItem>()
    val previousFilePaths = mutableListOf<String?>()
    val trashed = mutableListOf<TodoItem>()
    private val todos = initialTodos

    override suspend fun getTodos(): List<TodoItem> = todos.toList()

    override suspend fun getWidgetTodoSnapshot(today: LocalDate): WidgetTodoSnapshot =
        WidgetTodoSnapshot.fromTodos(todos, today)

    override suspend fun saveTodo(todo: TodoItem, previousFilePath: String?): TodoItem {
        saved += todo
        previousFilePaths += previousFilePath
        todos.removeAll { it.id == todo.id }
        todos += todo
        return todo
    }

    override suspend fun deleteTodo(todo: TodoItem) {
        todos.removeAll { it.id == todo.id }
    }

    override suspend fun trashTodo(todo: TodoItem): TodoItem {
        todos.removeAll { it.id == todo.id }
        val relativePath = todo.filePath.removePrefix("Tasks/")
        val result = todo.copy(filePath = "Tasks/.trash/$relativePath")
        trashed += result
        return result
    }

    override suspend fun restoreTodo(todo: TodoItem): TodoItem {
        val restored = todo.copy(filePath = "Tasks/${todo.filePath.removePrefix("Tasks/.trash/")}")
        todos += restored
        trashed.removeAll { it.id == todo.id }
        return restored
    }

    override suspend fun getTrashedTodos(): List<TodoItem> = trashed.toList()
    override suspend fun purgeOldTrash(): Int = 0

    override suspend fun bulkSave(todos: List<TodoItem>) {
        todos.forEach { saveTodo(it) }
    }

    override suspend fun bulkTrash(todos: List<TodoItem>): List<TodoItem> =
        todos.map { trashTodo(it) }

    override suspend fun bulkRestore(todos: List<TodoItem>): List<TodoItem> =
        todos.map { restoreTodo(it) }

    override suspend fun pull(): GitRepository.GitResult = GitRepository.GitResult.Success
    override suspend fun push(message: String): GitRepository.GitResult = GitRepository.GitResult.Success
    override suspend fun resolveConflicts(
        files: List<String>,
        keepLocal: Boolean
    ): GitRepository.GitResult = GitRepository.GitResult.Success
}
