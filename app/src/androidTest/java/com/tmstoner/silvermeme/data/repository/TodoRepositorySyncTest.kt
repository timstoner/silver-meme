package com.tmstoner.silvermeme.data.repository

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.storage.SettingsSnapshot
import com.tmstoner.silvermeme.data.storage.SettingsStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TodoRepositorySyncTest {
    @Test
    fun saveReportsPullErrorAndPendingSyncClearsAfterSuccessfulRetry() = runBlocking {
        val vault = temporaryVault()
        val settings = FakeSettingsStore(vault.absolutePath)
        val git = FakeGitOperations().apply {
            cloneResult = GitRepository.GitResult.Error("remote unavailable")
        }
        try {
            val repository = TodoRepository(
                context = targetContext(),
                settings = settings,
                gitRepository = git
            )
            val nextEvent = async(start = CoroutineStart.UNDISPATCHED) {
                repository.automaticSyncEvents.first()
            }

            repository.saveTodo(TodoItem(id = "offline-task", title = "Write locally"))

            assertEquals(
                GitRepository.GitResult.Error("remote unavailable"),
                nextEvent.await()
            )
            assertTrue(settings.pendingSyncState.value)
            assertEquals(1, git.cloneOrPullCalls)
            assertEquals(0, git.commitAndPushCalls)

            val recoveredEvent = async(start = CoroutineStart.UNDISPATCHED) {
                repository.automaticSyncEvents.first()
            }
            git.cloneResult = GitRepository.GitResult.Success

            assertEquals(GitRepository.GitResult.Success, repository.retryPendingSync())
            assertEquals(GitRepository.GitResult.Success, recoveredEvent.await())
            assertFalse(settings.pendingSyncState.value)
            assertEquals(2, git.cloneOrPullCalls)
            assertEquals(1, git.commitAndPushCalls)
        } finally {
            vault.deleteRecursively()
        }
    }

    @Test
    fun saveReportsPushConflictAndRetainsPendingSync() = runBlocking {
        val vault = temporaryVault()
        val settings = FakeSettingsStore(vault.absolutePath)
        val conflict = GitRepository.GitResult.Conflict(listOf("Tasks/overlap.md"))
        val git = FakeGitOperations().apply { pushResult = conflict }
        try {
            val repository = TodoRepository(
                context = targetContext(),
                settings = settings,
                gitRepository = git
            )
            val nextEvent = async(start = CoroutineStart.UNDISPATCHED) {
                repository.automaticSyncEvents.first()
            }

            repository.saveTodo(TodoItem(id = "conflicting-task", title = "Update locally"))

            assertEquals(conflict, nextEvent.await())
            assertTrue(settings.pendingSyncState.value)
            assertEquals(1, git.cloneOrPullCalls)
            assertEquals(1, git.commitAndPushCalls)
        } finally {
            vault.deleteRecursively()
        }
    }

    private fun targetContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun temporaryVault(): File =
        File(targetContext().cacheDir, "repository-sync-${UUID.randomUUID()}").apply { mkdirs() }
}

private class FakeSettingsStore(private val configuredVaultPath: String) : SettingsStore {
    override val gitRemoteUrl: Flow<String> = MutableStateFlow("configured-fake-remote")
    override val gitUsername: Flow<String> = MutableStateFlow("")
    override val gitToken: Flow<String> = MutableStateFlow("")
    override val vaultPath: Flow<String> = MutableStateFlow(configuredVaultPath)
    override val authorName: Flow<String> = MutableStateFlow("Test Author")
    override val authorEmail: Flow<String> = MutableStateFlow("test@local")
    override val themeMode: Flow<String> = MutableStateFlow("system")
    override val lastFilterState: Flow<String> = MutableStateFlow("")
    val pendingSyncState = MutableStateFlow(false)
    override val pendingSync: Flow<Boolean> = pendingSyncState

    override suspend fun snapshot() = SettingsSnapshot(
        gitRemoteUrl = "configured-fake-remote",
        gitUsername = "",
        gitToken = "",
        vaultPath = configuredVaultPath,
        authorName = "Test Author",
        authorEmail = "test@local",
        pendingSync = pendingSyncState.value
    )

    override suspend fun setGitRemoteUrl(url: String) = Unit
    override suspend fun setGitUsername(username: String) = Unit
    override suspend fun setGitToken(token: String) = Unit
    override suspend fun setVaultPath(path: String) = Unit
    override suspend fun setAuthorName(name: String) = Unit
    override suspend fun setAuthorEmail(email: String) = Unit
    override suspend fun setThemeMode(mode: String) = Unit
    override suspend fun setLastFilterState(serialized: String) = Unit
    override suspend fun setPendingSync(pending: Boolean) {
        pendingSyncState.value = pending
    }
}

private class FakeGitOperations : GitOperations {
    var cloneResult: GitRepository.GitResult = GitRepository.GitResult.Success
    var pushResult: GitRepository.GitResult = GitRepository.GitResult.Success
    var cloneOrPullCalls = 0
    var commitAndPushCalls = 0

    override suspend fun cloneOrPull(
        remoteUrl: String,
        localDir: File,
        username: String,
        token: String,
        authorName: String,
        authorEmail: String
    ): GitRepository.GitResult {
        cloneOrPullCalls++
        return cloneResult
    }

    override suspend fun pull(
        localDir: File,
        username: String,
        token: String
    ): GitRepository.GitResult = GitRepository.GitResult.Success

    override suspend fun commitAndPush(
        localDir: File,
        username: String,
        token: String,
        message: String,
        authorName: String,
        authorEmail: String
    ): GitRepository.GitResult {
        commitAndPushCalls++
        return pushResult
    }

    override suspend fun resolveConflicts(
        localDir: File,
        files: List<String>,
        keepLocal: Boolean,
        username: String,
        token: String,
        authorName: String,
        authorEmail: String
    ): GitRepository.GitResult = GitRepository.GitResult.Success

    override suspend fun initLocal(localDir: File): GitRepository.GitResult =
        GitRepository.GitResult.Success
}
