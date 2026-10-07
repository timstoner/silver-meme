package com.tmstoner.silvermeme

import android.app.Application
import com.tmstoner.silvermeme.data.repository.GitRepository
import com.tmstoner.silvermeme.data.repository.TodoRepository
import com.tmstoner.silvermeme.data.storage.SettingsDataStore
import com.tmstoner.silvermeme.domain.widget.LoadWidgetTodosUseCase
import com.tmstoner.silvermeme.notifications.NotificationScheduler
import com.tmstoner.silvermeme.sync.BackgroundSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Application class that acts as a simple service locator.
 * Dependencies are created lazily so startup remains fast.
 */
class SilverMemeApplication : Application() {

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

    val backgroundSyncScheduler: BackgroundSyncScheduler by lazy {
        BackgroundSyncScheduler(applicationContext)
    }

    /** App-lifetime scope for work that outlives any screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Keep the periodic sync worker in line with the "Background sync" setting:
        // on start-up and whenever the user flips the switch in Settings.
        appScope.launch {
            settingsDataStore.backgroundSync
                .distinctUntilChanged()
                .collect { backgroundSyncScheduler.apply(it) }
        }
    }
}
