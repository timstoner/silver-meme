package com.tmstoner.silvermeme

import android.app.Application
import com.tmstoner.silvermeme.data.repository.GitRepository
import com.tmstoner.silvermeme.data.repository.TodoRepository
import com.tmstoner.silvermeme.data.storage.SettingsDataStore

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
}
