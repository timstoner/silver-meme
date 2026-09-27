package com.tmstoner.silvermeme.data.repository

import android.content.Context
import com.tmstoner.silvermeme.data.model.SampleDataProvider
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.model.WidgetTodoSnapshot
import com.tmstoner.silvermeme.data.storage.MarkdownFileManager
import com.tmstoner.silvermeme.data.storage.SettingsStore
import com.tmstoner.silvermeme.data.storage.SettingsSnapshot
import com.tmstoner.silvermeme.notifications.PendingSyncWorker
import com.tmstoner.silvermeme.widgets.TodoWidgetDeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Central repository that coordinates the [MarkdownFileManager] (local file I/O)
 * and [GitRepository] (remote sync) to provide a single source of truth for
 * TODO items.
 */
class TodoRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val gitRepository: GitOperations = GitRepository()
) : TodoDataSource {

    private val _automaticSyncEvents = MutableSharedFlow<GitRepository.GitResult>(
        extraBufferCapacity = 1
    )
    override val automaticSyncEvents = _automaticSyncEvents.asSharedFlow()
    override val pendingSync: Flow<Boolean> = settings.pendingSync

    @Volatile private var cachedVaultPath: String? = null
    @Volatile private var cachedMarkdownFileManager: MarkdownFileManager? = null
    private val syncMutex = Mutex()

    // ── Vault directory resolution ────────────────────────────────────────────

    /**
     * Resolves the local vault directory from settings, falling back to the
     * app's external files directory.
     */
    private suspend fun resolveVaultDir(snapshot: SettingsSnapshot): File {
        return if (snapshot.vaultPath.isNotBlank()) {
            File(snapshot.vaultPath)
        } else {
            val dir = context.getExternalFilesDir("vault")
                ?: File(context.filesDir, "vault")
            settings.setVaultPath(dir.absolutePath)
            dir
        }
    }

    private suspend fun markdownFileManager(snapshot: SettingsSnapshot): MarkdownFileManager {
        val vaultDir = resolveVaultDir(snapshot)
        if (!vaultDir.exists()) vaultDir.mkdirs()
        val path = vaultDir.absoluteFile.normalize().path
        synchronized(this) {
            if (cachedVaultPath != path || cachedMarkdownFileManager == null) {
                cachedVaultPath = path
                cachedMarkdownFileManager = MarkdownFileManager(vaultDir)
            }
            return cachedMarkdownFileManager!!
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns all TODO items found in the local vault as a one-shot list. */
    override suspend fun getTodos(): List<TodoItem> = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        markdownFileManager(snapshot).getAllTodos()
    }

    /** Returns incomplete overdue and due-today tasks for widget rendering. */
    override suspend fun getWidgetTodoSnapshot(today: LocalDate): WidgetTodoSnapshot = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        WidgetTodoSnapshot.fromTodos(
            todos = markdownFileManager(snapshot).getAllTodos(),
            today = today
        )
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Saves [todo] to its backing markdown file and (if git is configured)
     * commits and pushes the change.
     *
     * @return The saved [TodoItem] with an updated [TodoItem.filePath].
     */
    override suspend fun saveTodo(todo: TodoItem, previousFilePath: String?): TodoItem = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        val manager = markdownFileManager(snapshot)
        val saved = manager.saveTodo(todo, previousFilePath)
        syncIfConfigured(snapshot)
        TodoWidgetDeepLinks.refreshWidgets(context)
        saved
    }

    /** Deletes [todo] and (if git is configured) commits and pushes the deletion. */
    override suspend fun deleteTodo(todo: TodoItem) = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        markdownFileManager(snapshot).deleteTodo(todo)
        syncIfConfigured(snapshot)
        TodoWidgetDeepLinks.refreshWidgets(context)
    }

    /** Moves [todo] to trash (Tasks/.trash/) and syncs. Returns updated item with new filePath. */
    override suspend fun trashTodo(todo: TodoItem): TodoItem = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        val trashed = markdownFileManager(snapshot).trashTodo(todo)
        syncIfConfigured(snapshot)
        TodoWidgetDeepLinks.refreshWidgets(context)
        trashed
    }

    /** Moves a trashed todo back to Tasks/ and syncs. Returns restored item. */
    override suspend fun restoreTodo(todo: TodoItem): TodoItem = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        val restored = markdownFileManager(snapshot).restoreTodo(todo)
        syncIfConfigured(snapshot)
        TodoWidgetDeepLinks.refreshWidgets(context)
        restored
    }

    /** Returns all items currently in Tasks/.trash/. */
    override suspend fun getTrashedTodos(): List<TodoItem> = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        markdownFileManager(snapshot).getTrashedTodos()
    }

    /** Purges trash items older than 30 days; returns count deleted. */
    override suspend fun purgeOldTrash(): Int = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        val count = markdownFileManager(snapshot).purgeOldTrash()
        if (count > 0) {
            syncIfConfigured(snapshot)
            TodoWidgetDeepLinks.refreshWidgets(context)
        }
        count
    }

    /** Saves multiple todos in a single batch and syncs once at the end. */
    override suspend fun bulkSave(todos: List<TodoItem>) = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        val manager = markdownFileManager(snapshot)
        todos.forEach { manager.saveTodo(it, it.filePath.takeIf { p -> p.isNotBlank() }) }
        syncIfConfigured(snapshot)
        TodoWidgetDeepLinks.refreshWidgets(context)
    }

    /** Moves multiple todos to trash in a single batch and syncs once at the end. */
    override suspend fun bulkTrash(todos: List<TodoItem>): List<TodoItem> = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        val manager = markdownFileManager(snapshot)
        val trashed = todos.map { manager.trashTodo(it) }
        syncIfConfigured(snapshot)
        TodoWidgetDeepLinks.refreshWidgets(context)
        trashed
    }

    /** Restores multiple todos in a single batch and syncs once at the end. */
    override suspend fun bulkRestore(todos: List<TodoItem>): List<TodoItem> = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        val manager = markdownFileManager(snapshot)
        val restored = todos.map { manager.restoreTodo(it) }
        syncIfConfigured(snapshot)
        TodoWidgetDeepLinks.refreshWidgets(context)
        restored
    }

    // ── Git sync ──────────────────────────────────────────────────────────────

    /**
     * Pulls the latest changes from the remote repository.
     *
     * If no remote URL is configured the operation is skipped and a success
     * result is returned to avoid surfacing errors to the user.
     */
    override suspend fun pull(): GitRepository.GitResult = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        pull(snapshot).also { result ->
            if (result is GitRepository.GitResult.Success) {
                TodoWidgetDeepLinks.refreshWidgets(context)
            }
        }
    }

    /**
     * Commits and pushes all local changes to the remote repository.
     * No-op if git is not configured.
     */
    override suspend fun push(message: String): GitRepository.GitResult =
        withContext(Dispatchers.IO) {
            val snapshot = settings.snapshot()
            push(snapshot, message).also { result ->
                if (result is GitRepository.GitResult.Success && snapshot.pendingSync) {
                    settings.setPendingSync(false)
                    _automaticSyncEvents.emit(GitRepository.GitResult.Success)
                }
            }
        }

    override suspend fun resolveConflicts(
        files: List<String>,
        keepLocal: Boolean
    ): GitRepository.GitResult = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        if (!snapshot.isGitConfigured) return@withContext GitRepository.GitResult.Success
        gitRepository.resolveConflicts(
            localDir = resolveVaultDir(snapshot),
            files = files,
            keepLocal = keepLocal,
            username = snapshot.gitUsername,
            token = snapshot.gitToken,
            authorName = snapshot.authorName,
            authorEmail = snapshot.authorEmail
        ).also { result ->
            if (result is GitRepository.GitResult.Success) {
                settings.setPendingSync(false)
                _automaticSyncEvents.emit(GitRepository.GitResult.Success)
                TodoWidgetDeepLinks.refreshWidgets(context)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun syncIfConfigured(snapshot: SettingsSnapshot) {
        if (!snapshot.isGitConfigured) return
        // Persist before touching the network so a process death cannot lose the
        // obligation to sync a successful local write.
        settings.setPendingSync(true)
        enqueuePendingSync()
        syncMutex.withLock {
            when (val pullResult = pull(snapshot)) {
                GitRepository.GitResult.Success -> Unit
                is GitRepository.GitResult.Error,
                is GitRepository.GitResult.Conflict -> {
                    _automaticSyncEvents.emit(pullResult)
                    return@withLock
                }
            }
            when (val pushResult = push(snapshot, "SilverMeme: sync vault")) {
                GitRepository.GitResult.Success -> {
                    settings.setPendingSync(false)
                    _automaticSyncEvents.emit(GitRepository.GitResult.Success)
                }
                is GitRepository.GitResult.Error,
                is GitRepository.GitResult.Conflict -> _automaticSyncEvents.emit(pushResult)
            }
        }
    }

    /**
     * Called by network-constrained WorkManager retries. Pending state is only
     * cleared after both pull and push complete successfully.
     */
    override suspend fun retryPendingSync(): GitRepository.GitResult = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        if (!snapshot.pendingSync) return@withContext GitRepository.GitResult.Success
        if (!snapshot.isGitConfigured) {
            settings.setPendingSync(false)
            return@withContext GitRepository.GitResult.Success
        }
        syncMutex.withLock {
            when (val pullResult = pull(snapshot)) {
                GitRepository.GitResult.Success -> Unit
                else -> {
                    _automaticSyncEvents.emit(pullResult)
                    return@withContext pullResult
                }
            }
            val result = push(snapshot, "SilverMeme: retry pending vault sync")
            if (result is GitRepository.GitResult.Success) {
                settings.setPendingSync(false)
                _automaticSyncEvents.emit(GitRepository.GitResult.Success)
            } else {
                _automaticSyncEvents.emit(result)
            }
            result
        }
    }

    private suspend fun pull(snapshot: SettingsSnapshot): GitRepository.GitResult {
        if (!snapshot.isGitConfigured) return GitRepository.GitResult.Success
        return gitRepository.cloneOrPull(
            remoteUrl = snapshot.gitRemoteUrl,
            localDir = resolveVaultDir(snapshot),
            username = snapshot.gitUsername,
            token = snapshot.gitToken,
            authorName = snapshot.authorName,
            authorEmail = snapshot.authorEmail
        )
    }

    private suspend fun push(
        snapshot: SettingsSnapshot,
        message: String
    ): GitRepository.GitResult {
        if (!snapshot.isGitConfigured) return GitRepository.GitResult.Success
        return gitRepository.commitAndPush(
            localDir = resolveVaultDir(snapshot),
            username = snapshot.gitUsername,
            token = snapshot.gitToken,
            message = message,
            authorName = snapshot.authorName,
            authorEmail = snapshot.authorEmail
        )
    }

    private fun enqueuePendingSync() {
        PendingSyncWorker.enqueue(context)
    }

    /**
     * Initializes the vault with sample TODO items if no existing tasks are present.
     * This provides a good onboarding experience for first-time users, showing them
     * the app's capabilities with realistic lorem ipsum content and various task states.
     *
     * Safe to call multiple times — no-op if tasks already exist.
     */
    suspend fun initializeSampleDataIfNeeded() = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        val manager = markdownFileManager(snapshot)
        val existingTodos = manager.getAllTodos()
        if (existingTodos.isNotEmpty()) {
            // Vault already has data; don't add samples
            return@withContext
        }

        // Check if the tasks folder exists but is empty
        val vaultDir = resolveVaultDir(snapshot)
        val tasksDir = File(vaultDir, MarkdownFileManager.TASKS_FOLDER)

        val hasMarkdownFiles = if (tasksDir.exists()) {
            tasksDir.walk().any { it.isFile && it.extension == "md" }
        } else {
            false
        }

        if (!hasMarkdownFiles) {
            // Vault is empty; populate with sample data
            val sampleTodos = SampleDataProvider.generateSampleTodos()
            sampleTodos.forEach { manager.saveTodo(it) }
            syncIfConfigured(snapshot)
            TodoWidgetDeepLinks.refreshWidgets(context)
        }
    }
}
