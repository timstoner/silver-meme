package com.tmstoner.silvermeme.data.repository

import android.content.Context
import com.tmstoner.silvermeme.data.model.NoteIndex
import com.tmstoner.silvermeme.data.model.SampleDataProvider
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.model.VaultRegistry
import com.tmstoner.silvermeme.data.model.WidgetTodoSnapshot
import com.tmstoner.silvermeme.data.storage.MarkdownFileManager
import com.tmstoner.silvermeme.data.storage.SettingsStore
import com.tmstoner.silvermeme.widgets.TodoWidgetDeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    private val gitRepository: GitRepository = GitRepository()
) : TodoDataSource {

    // ── Vault directory resolution ────────────────────────────────────────────

    /**
     * Resolves the ACTIVE vault's local directory from settings (Track G2),
     * falling back to the app's external files directory. Only the default vault
     * can have a blank path; added vaults get their own directory when created.
     */
    private suspend fun resolveVaultDir(): File {
        val savedPath = settings.vaultPath.first()
        return if (savedPath.isNotBlank()) {
            File(savedPath)
        } else {
            val dir = context.getExternalFilesDir("vault")
                ?: File(context.filesDir, "vault")
            settings.setVaultPath(dir.absolutePath)
            dir
        }
    }

    private suspend fun markdownFileManager(): MarkdownFileManager {
        val vaultDir = resolveVaultDir()
        if (!vaultDir.exists()) vaultDir.mkdirs()
        return MarkdownFileManager(vaultDir)
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns all TODO items found in the local vault as a one-shot list. */
    override suspend fun getTodos(): List<TodoItem> = withContext(Dispatchers.IO) {
        markdownFileManager().getAllTodos()
    }

    /** Returns incomplete overdue and due-today tasks for widget rendering. */
    override suspend fun getWidgetTodoSnapshot(today: LocalDate): WidgetTodoSnapshot = withContext(Dispatchers.IO) {
        WidgetTodoSnapshot.fromTodos(
            todos = markdownFileManager().getAllTodos(),
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
        val manager = markdownFileManager()
        val saved = manager.saveTodo(todo, previousFilePath)
        syncIfConfigured()
        TodoWidgetDeepLinks.refreshWidgets(context)
        saved
    }

    /** Deletes [todo] and (if git is configured) commits and pushes the deletion. */
    override suspend fun deleteTodo(todo: TodoItem) = withContext(Dispatchers.IO) {
        markdownFileManager().deleteTodo(todo)
        syncIfConfigured()
        TodoWidgetDeepLinks.refreshWidgets(context)
    }

    /** Moves [todo] to trash (Tasks/.trash/) and syncs. Returns updated item with new filePath. */
    override suspend fun trashTodo(todo: TodoItem): TodoItem = withContext(Dispatchers.IO) {
        val trashed = markdownFileManager().trashTodo(todo)
        syncIfConfigured()
        TodoWidgetDeepLinks.refreshWidgets(context)
        trashed
    }

    /** Moves a trashed todo back to Tasks/ and syncs. Returns restored item. */
    override suspend fun restoreTodo(todo: TodoItem): TodoItem = withContext(Dispatchers.IO) {
        val restored = markdownFileManager().restoreTodo(todo)
        syncIfConfigured()
        TodoWidgetDeepLinks.refreshWidgets(context)
        restored
    }

    /** Returns all items currently in Tasks/.trash/. */
    override suspend fun getTrashedTodos(): List<TodoItem> = withContext(Dispatchers.IO) {
        markdownFileManager().getTrashedTodos()
    }

    /** Purges trash items older than 30 days; returns count deleted. */
    override suspend fun purgeOldTrash(): Int = withContext(Dispatchers.IO) {
        val count = markdownFileManager().purgeOldTrash()
        if (count > 0) {
            syncIfConfigured()
            TodoWidgetDeepLinks.refreshWidgets(context)
        }
        count
    }

    /** Saves multiple todos in a single batch and syncs once at the end. */
    override suspend fun bulkSave(todos: List<TodoItem>) = withContext(Dispatchers.IO) {
        val manager = markdownFileManager()
        todos.forEach { manager.saveTodo(it, it.filePath.takeIf { p -> p.isNotBlank() }) }
        syncIfConfigured()
        TodoWidgetDeepLinks.refreshWidgets(context)
    }

    /** Moves multiple todos to trash in a single batch and syncs once at the end. */
    override suspend fun bulkTrash(todos: List<TodoItem>) = withContext(Dispatchers.IO) {
        val manager = markdownFileManager()
        todos.forEach { manager.trashTodo(it) }
        syncIfConfigured()
        TodoWidgetDeepLinks.refreshWidgets(context)
    }

    // ── Git sync ──────────────────────────────────────────────────────────────

    /**
     * Pulls the latest changes from the remote repository.
     *
     * If no remote URL is configured the operation is skipped and a success
     * result is returned to avoid surfacing errors to the user.
     */
    override suspend fun getNoteIndex(): NoteIndex = withContext(Dispatchers.IO) {
        val vaultDir = resolveVaultDir()
        NoteIndex(vaultName = vaultDir.name, noteNames = MarkdownFileManager(vaultDir).listNoteNames())
    }

    override suspend fun pull(): GitRepository.GitResult = withContext(Dispatchers.IO) {
        val remoteUrl = settings.gitRemoteUrl.first()
        if (remoteUrl.isBlank()) return@withContext GitRepository.GitResult.Success

        val username  = settings.gitUsername.first()
        val token     = settings.gitToken.first()
        val vaultDir  = resolveVaultDir()

        gitRepository.cloneOrPull(remoteUrl, vaultDir, username, token).also { result ->
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
            val remoteUrl   = settings.gitRemoteUrl.first()
            if (remoteUrl.isBlank()) return@withContext GitRepository.GitResult.Success

            val username    = settings.gitUsername.first()
            val token       = settings.gitToken.first()
            val authorName  = settings.authorName.first()
            val authorEmail = settings.authorEmail.first()
            val vaultDir    = resolveVaultDir()

            gitRepository.commitAndPush(vaultDir, username, token, message, authorName, authorEmail)
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun syncIfConfigured() {
        val remoteUrl = settings.gitRemoteUrl.first()
        if (remoteUrl.isBlank()) return
        // Pull first to integrate remote changes before pushing; a non-fast-forward
        // push is silently rejected by the remote otherwise.
        val pullResult = pull()
        if (pullResult is GitRepository.GitResult.Error) {
            // Log the failure but don't block: the local write already succeeded.
            android.util.Log.w("TodoRepository", "pull failed before push: ${pullResult.message}")
            return
        }
        push("SilverMeme: sync vault")
    }

    /**
     * Initializes the vault with sample TODO items if no existing tasks are present.
     * This provides a good onboarding experience for first-time users, showing them
     * the app's capabilities with realistic lorem ipsum content and various task states.
     *
     * Safe to call multiple times — no-op if tasks already exist. Only the default
     * vault gets samples: a vault added later is meant to be cloned or filled by the
     * user, and sample files would make the first clone into it fail.
     */
    suspend fun initializeSampleDataIfNeeded() = withContext(Dispatchers.IO) {
        if (settings.activeVaultId.first() != VaultRegistry.DEFAULT_ID) return@withContext
        val existingTodos = getTodos()
        if (existingTodos.isNotEmpty()) {
            // Vault already has data; don't add samples
            return@withContext
        }

        // Check if the tasks folder exists but is empty
        val vaultDir = resolveVaultDir()
        val tasksDir = File(vaultDir, MarkdownFileManager.TASKS_FOLDER)

        val hasMarkdownFiles = if (tasksDir.exists()) {
            tasksDir.walk().any { it.isFile && it.extension == "md" }
        } else {
            false
        }

        if (!hasMarkdownFiles) {
            // Vault is empty; populate with sample data
            val sampleTodos = SampleDataProvider.generateSampleTodos()
            bulkSave(sampleTodos)
        }
    }
}
