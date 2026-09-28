package com.tmstoner.silvermeme

import android.app.Application
import com.tmstoner.silvermeme.data.repository.GitRepository
import com.tmstoner.silvermeme.data.repository.TodoRepository
import com.tmstoner.silvermeme.data.storage.SettingsDataStore
import com.tmstoner.silvermeme.domain.widget.LoadWidgetTodosUseCase
import com.tmstoner.silvermeme.notifications.NotificationScheduler
import com.tmstoner.silvermeme.notifications.PendingSyncWorker
import com.tmstoner.silvermeme.viewmodel.DashboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application class that acts as a simple service locator.
 * Dependencies are created lazily so startup remains fast.
 */
class SilverMemeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                if (settingsDataStore.pendingSync.first()) {
                    PendingSyncWorker.enqueue(applicationContext)
                }
            }
        }
    }

    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(applicationContext)
    }

    val todoRepository: TodoRepository by lazy {
        TodoRepository(
            context       = applicationContext,
            settings      = settingsDataStore,
            gitRepository = GitRepository()
        )
    }

    val loadWidgetTodosUseCase: LoadWidgetTodosUseCase by lazy {
        LoadWidgetTodosUseCase(todoRepository)
    }

    val notificationScheduler: NotificationScheduler by lazy {
        NotificationScheduler(applicationContext)
    }

    /** Factory for the dashboard's local-only, device-layout view model. */
    val dashboardViewModelFactory: DashboardViewModel.Factory by lazy {
        DashboardViewModel.Factory(todoRepository, settingsDataStore)
    }
}
