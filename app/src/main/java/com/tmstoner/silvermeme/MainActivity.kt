package com.tmstoner.silvermeme

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.tmstoner.silvermeme.ui.navigation.AppNavGraph
import com.tmstoner.silvermeme.ui.theme.SilvermemeTheme
import com.tmstoner.silvermeme.viewmodel.SettingsViewModel
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import com.tmstoner.silvermeme.widgets.TodoWidgetDeepLinks
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val deepLinkIntents = MutableSharedFlow<Intent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SilverMemeApplication

        // Initialize sample data on first launch if vault is empty
        lifecycleScope.launch {
            app.todoRepository.initializeSampleDataIfNeeded()
        }

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(app.settingsDataStore)
            )
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()

            // Resolve "system" / "light" / "dark" preference into a boolean for the theme.
            val useDarkTheme = when (settingsState.themeMode) {
                "light" -> false
                "dark"  -> true
                else    -> isSystemInDarkTheme()
            }

            // Detect tablet landscape: width ≥ Medium (≥ 600 dp)
            val windowSizeClass = calculateWindowSizeClass(this)
            val isTablet = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

            SilvermemeTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val todoViewModel: TodoViewModel = viewModel(
                        factory = TodoViewModel.Factory(app.todoRepository, app.settingsDataStore, app.notificationScheduler)
                    )
                    val todoUiState by todoViewModel.uiState.collectAsStateWithLifecycle()

                    AppNavGraph(
                        navController     = navController,
                        todoViewModel     = todoViewModel,
                        settingsViewModel = settingsViewModel,
                        isTablet          = isTablet
                    )

                    LaunchedEffect(navController) {
                        intent?.let(navController::handleDeepLink)
                        deepLinkIntents.collect { navController.handleDeepLink(it) }
                    }

                    LaunchedEffect(todoUiState.todos) {
                        TodoWidgetDeepLinks.refreshWidgets(applicationContext)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkIntents.tryEmit(intent)
    }
}
