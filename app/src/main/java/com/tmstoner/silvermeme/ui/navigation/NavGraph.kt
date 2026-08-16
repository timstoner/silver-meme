package com.tmstoner.silvermeme.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.ui.screens.SettingsScreen
import com.tmstoner.silvermeme.ui.screens.TabletTodoLayout
import com.tmstoner.silvermeme.ui.screens.TodoDetailScreen
import com.tmstoner.silvermeme.ui.screens.TodoListScreen
import com.tmstoner.silvermeme.ui.screens.TrashScreen
import com.tmstoner.silvermeme.viewmodel.SettingsViewModel
import com.tmstoner.silvermeme.viewmodel.TodoViewModel

object Routes {
    const val TODO_LIST = "todo_list"
    const val TODO_NEW = "todo_new"
    const val TODO_EDIT = "todo_edit/{todoId}"
    const val SETTINGS = "settings"
    const val TRASH = "trash"

    fun todoEdit(todoId: String) = "todo_edit/$todoId"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    todoViewModel: TodoViewModel,
    settingsViewModel: SettingsViewModel,
    isTablet: Boolean = false
) {
    NavHost(
        navController = navController,
        startDestination = Routes.TODO_LIST
    ) {
        composable(
            route = Routes.TODO_LIST,
            deepLinks = listOf(
                navDeepLink { uriPattern = "silvermeme://todo/list" }
            )
        ) {
            if (isTablet) {
                TabletTodoLayout(
                    viewModel      = todoViewModel,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenTrash    = { navController.navigate(Routes.TRASH) }
                )
            } else {
                TodoListScreen(
                    viewModel      = todoViewModel,
                    onAddTodo      = { navController.navigate(Routes.TODO_NEW) },
                    onEditTodo     = { todo -> navController.navigate(Routes.todoEdit(todo.id)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenTrash    = { navController.navigate(Routes.TRASH) }
                )
            }
        }

        composable(
            route = Routes.TODO_NEW,
            deepLinks = listOf(
                navDeepLink { uriPattern = "silvermeme://todo/new" }
            )
        ) {
            TodoDetailScreen(
                viewModel = todoViewModel,
                existingTodo = null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.TODO_EDIT,
            arguments = listOf(navArgument("todoId") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "silvermeme://todo/edit?todoId={todoId}" }
            )
        ) { backStackEntry ->
            val todoId = backStackEntry.arguments?.getString("todoId").orEmpty()
            val uiState by todoViewModel.uiState.collectAsStateWithLifecycle()
            val todo = uiState.todos.firstOrNull { it.id == todoId }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (todo == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.todo_not_found),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                TodoDetailScreen(
                    viewModel = todoViewModel,
                    existingTodo = todo,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TRASH) {
            TrashScreen(
                viewModel = todoViewModel,
                onBack    = { navController.popBackStack() }
            )
        }
    }
}
