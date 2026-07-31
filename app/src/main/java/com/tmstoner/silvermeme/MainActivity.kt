package com.tmstoner.silvermeme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.tmstoner.silvermeme.ui.navigation.AppNavGraph
import com.tmstoner.silvermeme.ui.theme.SilvermemeTheme
import com.tmstoner.silvermeme.viewmodel.SettingsViewModel
import com.tmstoner.silvermeme.viewmodel.TodoViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SilverMemeApplication

        setContent {
            SilvermemeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val todoViewModel: TodoViewModel = viewModel(
                        factory = TodoViewModel.Factory(app.todoRepository)
                    )

                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(app.settingsDataStore)
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
