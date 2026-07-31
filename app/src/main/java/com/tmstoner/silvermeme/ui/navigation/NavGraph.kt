package com.tmstoner.silvermeme.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tmstoner.silvermeme.ui.screens.SettingsScreen
import com.tmstoner.silvermeme.ui.screens.TodoDetailScreen
import com.tmstoner.silvermeme.ui.screens.TodoListScreen
import com.tmstoner.silvermeme.viewmodel.SettingsViewModel
import com.tmstoner.silvermeme.viewmodel.TodoViewModel

object Routes {
    const val TODO_LIST = "todo_list"
    const val TODO_NEW = "todo_new"
    const val TODO_EDIT = "todo_edit/{todoId}"
    const val SETTINGS = "settings"

    fun todoEdit(todoId: String) = "todo_edit/$todoId"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    todoViewModel: TodoViewModel,
    settingsViewModel: SettingsViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Routes.TODO_LIST
    ) {
        composable(Routes.TODO_LIST) {
            TodoListScreen(
                viewModel = todoViewModel,
                onAddTodo = { navController.navigate(Routes.TODO_NEW) },
                onEditTodo = { todo -> navController.navigate(Routes.todoEdit(todo.id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.TODO_NEW) {
            TodoDetailScreen(
                viewModel = todoViewModel,
                existingTodo = null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.TODO_EDIT,
            arguments = listOf(navArgument("todoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val todoId = backStackEntry.arguments?.getString("todoId").orEmpty()
            val uiState by todoViewModel.uiState.collectAsStateWithLifecycle()
            val todo = uiState.todos.firstOrNull { it.id == todoId }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
    }
}
