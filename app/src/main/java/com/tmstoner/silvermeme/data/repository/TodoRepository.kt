package com.tmstoner.silvermeme.data.repository

import android.content.Context
import com.tmstoner.silvermeme.data.model.TodoItem
import com.tmstoner.silvermeme.data.storage.MarkdownFileManager
import com.tmstoner.silvermeme.data.storage.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

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
     * Resolves the local vault directory from settings, falling back to the
     * app's external files directory.
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
        saved
    }

    /** Deletes [todo] and (if git is configured) commits and pushes the deletion. */
    override suspend fun deleteTodo(todo: TodoItem) = withContext(Dispatchers.IO) {
        markdownFileManager().deleteTodo(todo)
        syncIfConfigured()
    }

    /** Moves [todo] to trash (Tasks/.trash/) and syncs. Returns updated item with new filePath. */
    override suspend fun trashTodo(todo: TodoItem): TodoItem = withContext(Dispatchers.IO) {
        val trashed = markdownFileManager().trashTodo(todo)
        syncIfConfigured()
        trashed
    }

    /** Moves a trashed todo back to Tasks/ and syncs. Returns restored item. */
    override suspend fun restoreTodo(todo: TodoItem): TodoItem = withContext(Dispatchers.IO) {
        val restored = markdownFileManager().restoreTodo(todo)
        syncIfConfigured()
        restored
    }

    /** Returns all items currently in Tasks/.trash/. */
    override suspend fun getTrashedTodos(): List<TodoItem> = withContext(Dispatchers.IO) {
        markdownFileManager().getTrashedTodos()
    }

    /** Purges trash items older than 30 days; returns count deleted. */
    override suspend fun purgeOldTrash(): Int = withContext(Dispatchers.IO) {
        val count = markdownFileManager().purgeOldTrash()
        if (count > 0) syncIfConfigured()
        count
    }

    // ── Git sync ──────────────────────────────────────────────────────────────

    /**
     * Pulls the latest changes from the remote repository.
     *
     * If no remote URL is configured the operation is skipped and a success
     * result is returned to avoid surfacing errors to the user.
     */
    override suspend fun pull(): GitRepository.GitResult = withContext(Dispatchers.IO) {
        val remoteUrl = settings.gitRemoteUrl.first()
        if (remoteUrl.isBlank()) return@withContext GitRepository.GitResult.Success

        val username  = settings.gitUsername.first()
        val token     = settings.gitToken.first()
        val vaultDir  = resolveVaultDir()

        gitRepository.cloneOrPull(remoteUrl, vaultDir, username, token)
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
}
