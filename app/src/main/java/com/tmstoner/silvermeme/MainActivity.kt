package com.tmstoner.silvermeme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.tmstoner.silvermeme.ui.navigation.AppNavGraph
import com.tmstoner.silvermeme.ui.theme.SilvermemeTheme
import com.tmstoner.silvermeme.viewmodel.SettingsViewModel
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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
            val settingsState by settingsViewModel.settingsState.collectAsState()

            // Resolve "system" / "light" / "dark" preference into a boolean for the theme.
            val useDarkTheme = when (settingsState.themeMode) {
                "light" -> false
                "dark"  -> true
                else    -> isSystemInDarkTheme()
            }

            SilvermemeTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val todoViewModel: TodoViewModel = viewModel(
                        factory = TodoViewModel.Factory(app.todoRepository, app.settingsDataStore, app.notificationScheduler)
                    )

                    AppNavGraph(
                        navController     = navController,
                        todoViewModel     = todoViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
