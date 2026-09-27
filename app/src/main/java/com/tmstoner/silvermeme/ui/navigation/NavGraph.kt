package com.tmstoner.silvermeme.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.ui.screens.DashboardScreen
import com.tmstoner.silvermeme.ui.screens.SettingsScreen
import com.tmstoner.silvermeme.ui.screens.TabletTodoLayout
import com.tmstoner.silvermeme.ui.screens.TodoDetailScreen
import com.tmstoner.silvermeme.ui.screens.TodoListScreen
import com.tmstoner.silvermeme.ui.screens.TodoViewScreen
import com.tmstoner.silvermeme.ui.screens.TrashScreen
import com.tmstoner.silvermeme.viewmodel.SettingsViewModel
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import com.tmstoner.silvermeme.viewmodel.ProjectViewModel
import com.tmstoner.silvermeme.ui.screens.ProjectsScreen
import com.tmstoner.silvermeme.ui.screens.ProjectEditorScreen
import com.tmstoner.silvermeme.ui.screens.ProjectDetailScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

object Routes {
    const val DASHBOARD = "dashboard"
    const val TODO_LIST = "todo_list"
    const val TODO_NEW = "todo_new"
    const val TODO_VIEW = "todo_view/{todoId}"
    const val TODO_EDIT = "todo_edit/{todoId}"
    const val SETTINGS = "settings"
    const val TRASH = "trash"
    const val PROJECTS = "projects"
    const val PROJECT_NEW = "project_new"
    const val PROJECT_DETAIL = "project_detail/{projectId}"
    const val TODO_NEW_PROJECT = "todo_new/{projectId}"

    fun todoView(todoId: String) = "todo_view/$todoId"
    fun todoEdit(todoId: String) = "todo_edit/$todoId"
    fun projectDetail(projectId: String) = "project_detail/$projectId"
    fun todoNewForProject(projectId: String) = "todo_new/$projectId"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    todoViewModel: TodoViewModel,
    settingsViewModel: SettingsViewModel,
    projectViewModel: ProjectViewModel,
    isTablet: Boolean = false
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun navigateFromDrawer(route: String): Unit {
        if (currentRoute != route) {
            navController.navigate(
                route,
                navOptions {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            )
        }
        scope.launch { drawerState.close() }
    }

    val openNavigationDrawer: () -> Unit = fun() {
        scope.launch { drawerState.open() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.destination_projects)) },
                    icon = { androidx.compose.material3.Icon(Icons.Filled.Folder, contentDescription = null) },
                    selected = currentRoute == Routes.PROJECTS,
                    onClick = { navigateFromDrawer(Routes.PROJECTS) }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.destination_dashboard)) },
                    icon = { androidx.compose.material3.Icon(Icons.Filled.Home, contentDescription = null) },
                    selected = currentRoute == Routes.DASHBOARD,
                    onClick = { navigateFromDrawer(Routes.DASHBOARD) }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.destination_tasks)) },
                    icon = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    selected = currentRoute == Routes.TODO_LIST,
                    onClick = { navigateFromDrawer(Routes.TODO_LIST) }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.destination_trash)) },
                    icon = { androidx.compose.material3.Icon(Icons.Filled.Delete, contentDescription = null) },
                    selected = currentRoute == Routes.TRASH,
                    onClick = { navigateFromDrawer(Routes.TRASH) }
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.destination_settings)) },
                    icon = { androidx.compose.material3.Icon(Icons.Filled.Settings, contentDescription = null) },
                    selected = currentRoute == Routes.SETTINGS,
                    onClick = { navigateFromDrawer(Routes.SETTINGS) }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD
        ) {
        composable(
            route = Routes.DASHBOARD,
            deepLinks = listOf(
                navDeepLink { uriPattern = "silvermeme://todo/dashboard" }
            )
        ) {
            DashboardScreen(
                viewModel = todoViewModel,
                onOpenNavigationDrawer = openNavigationDrawer
            )
        }

        composable(Routes.PROJECTS) {
            ProjectsScreen(
                viewModel = projectViewModel,
                onOpen = { project -> navController.navigate(Routes.projectDetail(project.id)) },
                onCreate = { navController.navigate(Routes.PROJECT_NEW) },
                onOpenDrawer = openNavigationDrawer
            )
        }
        composable(Routes.PROJECT_NEW) {
            ProjectEditorScreen(projectViewModel, null) { navController.popBackStack() }
        }
        composable("project_edit/{projectId}", arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { entry ->
            val projects by projectViewModel.uiState.collectAsStateWithLifecycle()
            val project = projects.projects.firstOrNull { it.id == entry.arguments?.getString("projectId") }
            if (project != null) ProjectEditorScreen(projectViewModel, project) { navController.popBackStack() }
        }
        composable(Routes.PROJECT_DETAIL, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { entry ->
            ProjectDetailScreen(
                viewModel = projectViewModel,
                projectId = entry.arguments?.getString("projectId").orEmpty(),
                onBack = { navController.popBackStack() },
                onAddTask = { project -> navController.navigate(Routes.todoNewForProject(project.id)) },
                onEdit = { project -> navController.navigate("project_edit/${project.id}") }
            )
        }

        composable(
            route = Routes.TODO_LIST,
            deepLinks = listOf(
                navDeepLink { uriPattern = "silvermeme://todo/list" }
            )
        ) {
            if (isTablet) {
                TabletTodoLayout(
                    viewModel = todoViewModel,
                    onOpenNavigationDrawer = openNavigationDrawer,
                    onNavigateToDashboard = { navigateFromDrawer(Routes.DASHBOARD) }
                )
            } else {
                TodoListScreen(
                    viewModel = todoViewModel,
                    onAddTodo = { navController.navigate(Routes.TODO_NEW) },
                    onEditTodo = { todo -> navController.navigate(Routes.todoView(todo.id)) },
                    onOpenNavigationDrawer = openNavigationDrawer
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
            route = Routes.TODO_NEW_PROJECT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            val projectId = entry.arguments?.getString("projectId").orEmpty()
            val projectState by projectViewModel.uiState.collectAsStateWithLifecycle()
            val project = projectState.projects.firstOrNull { it.id == projectId }
            TodoDetailScreen(
                viewModel = todoViewModel,
                existingTodo = null,
                projectDefaults = project,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.TODO_VIEW,
            arguments = listOf(navArgument("todoId") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "silvermeme://todo/view?todoId={todoId}" }
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
                TodoViewScreen(
                    todo = todo,
                    onEdit = { navController.navigate(Routes.todoEdit(todo.id)) },
                    onBack = { navController.popBackStack() }
                )
            }
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
                onOpenNavigationDrawer = openNavigationDrawer
            )
        }

        composable(Routes.TRASH) {
            TrashScreen(
                viewModel = todoViewModel,
                onOpenNavigationDrawer = openNavigationDrawer
            )
        }
        }
    }
}
