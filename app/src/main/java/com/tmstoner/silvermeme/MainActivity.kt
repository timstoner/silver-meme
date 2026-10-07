package com.tmstoner.silvermeme

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.tmstoner.silvermeme.notifications.NotificationScheduler
import com.tmstoner.silvermeme.ui.navigation.AppNavGraph
import com.tmstoner.silvermeme.ui.theme.SilvermemeTheme
import com.tmstoner.silvermeme.viewmodel.SettingsViewModel
import com.tmstoner.silvermeme.viewmodel.TodoViewModel
import com.tmstoner.silvermeme.widgets.TodoWidgetDeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val deepLinkIntents = MutableSharedFlow<Intent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Reminders are silently skipped without POST_NOTIFICATIONS (Android 13+), so
    // tasks saved before the grant have none; schedule them once permission arrives.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) return@registerForActivityResult
            val app = application as SilverMemeApplication
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { app.notificationScheduler.rescheduleAll(app.todoRepository.getTodos()) }
            }
        }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SilverMemeApplication

        // Initialize sample data on first launch if vault is empty
        lifecycleScope.launch {
            app.todoRepository.initializeSampleDataIfNeeded()
        }

        // Multi-vault (G2): reminders and widgets follow the active vault. Vaults are
        // only switched from this activity's UI, so it is always alive when that happens.
        // Reminders already scheduled for the previous vault's tasks keep firing.
        lifecycleScope.launch(Dispatchers.IO) {
            app.settingsDataStore.activeVaultId.drop(1).collect {
                runCatching { app.notificationScheduler.rescheduleAll(app.todoRepository.getTodos()) }
                TodoWidgetDeepLinks.refreshWidgets(applicationContext)
            }
        }

        // Ask once per launch (not on rotation). Android stops showing the dialog
        // after the user denies it twice, so this can't nag indefinitely.
        if (savedInstanceState == null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationScheduler.hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
