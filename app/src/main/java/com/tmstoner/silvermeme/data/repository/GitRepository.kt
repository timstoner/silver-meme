package com.tmstoner.silvermeme.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

/**
 * Manages git operations on the local vault repository using JGit.
 *
 * Supports:
 *  - Cloning a remote repository for the first time
 *  - Pulling changes from the remote (fetch + merge)
 *  - Staging, committing, and pushing local changes back to the remote
 *
 * All network operations are dispatched on [Dispatchers.IO].
 */
class GitRepository {

    sealed class GitResult {
        object Success : GitResult()
        data class Error(val message: String, val cause: Throwable? = null) : GitResult()
    }

    // ── Clone ─────────────────────────────────────────────────────────────────

    /**
     * Clones [remoteUrl] into [localDir].
     * If [localDir] already contains a `.git` directory, the clone is skipped and
     * a pull is performed instead.
     */
    suspend fun cloneOrPull(
        remoteUrl: String,
        localDir: File,
        username: String,
        token: String
    ): GitResult = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider(username, token)
        try {
            if (File(localDir, ".git").exists()) {
                pull(localDir, credentials)
            } else {
                localDir.mkdirs()
                Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(localDir)
                    .setCredentialsProvider(credentials)
                    .call()
                    .close()
                GitResult.Success
            }
        } catch (e: GitAPIException) {
            GitResult.Error("Git clone/pull failed: ${e.message}", e)
        } catch (e: Exception) {
            GitResult.Error("Unexpected error: ${e.message}", e)
        }
    }

    // ── Pull ──────────────────────────────────────────────────────────────────

    /**
     * Pulls the latest changes from the configured remote.
     * Performs a hard reset on conflicts to keep the remote state authoritative
     * for the shared vault.
     */
    suspend fun pull(
        localDir: File,
        username: String,
        token: String
    ): GitResult = withContext(Dispatchers.IO) {
        try {
            pull(localDir, credentialsProvider(username, token))
        } catch (e: Exception) {
            GitResult.Error("Pull failed: ${e.message}", e)
        }
    }

    private fun pull(
        localDir: File,
        credentials: UsernamePasswordCredentialsProvider
    ): GitResult {
        Git.open(localDir).use { git ->
            val result = git.pull()
                .setCredentialsProvider(credentials)
                .call()
            return if (result.isSuccessful) {
                GitResult.Success
            } else {
                GitResult.Error("Pull completed with conflicts")
            }
        }
    }

    // ── Commit & push ─────────────────────────────────────────────────────────

    /**
     * Stages all changes in [localDir], creates a commit with [message], and
     * pushes to the remote.
     *
     * @param authorName  Git author name for the commit.
     * @param authorEmail Git author email for the commit.
     */
    suspend fun commitAndPush(
        localDir: File,
        username: String,
        token: String,
        message: String = "Update todos",
        authorName: String = "SilverMeme",
        authorEmail: String = "silvermeme@local"
    ): GitResult = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider(username, token)
        try {
            Git.open(localDir).use { git ->
                // Stage all changes (new, modified, deleted)
                git.add().addFilepattern(".").call()
                git.add().addFilepattern(".").setUpdate(true).call()

                // Only commit if there is something staged
                val status = git.status().call()
                val hasChanges = status.added.isNotEmpty()
                    || status.changed.isNotEmpty()
                    || status.removed.isNotEmpty()

                if (!hasChanges) return@withContext GitResult.Success

                git.commit()
                    .setAuthor(authorName, authorEmail)
                    .setCommitter(authorName, authorEmail)
                    .setMessage(message)
                    .call()

                git.push()
                    .setCredentialsProvider(credentials)
                    .call()

                GitResult.Success
            }
        } catch (e: GitAPIException) {
            GitResult.Error("Commit/push failed: ${e.message}", e)
        } catch (e: Exception) {
            GitResult.Error("Unexpected error during commit/push: ${e.message}", e)
        }
    }

    // ── Init (for local-only use without a remote) ────────────────────────────

    /**
     * Initialises a brand-new local git repository at [localDir].
     * Useful when the user wants to work locally without a remote.
     */
    suspend fun initLocal(localDir: File): GitResult = withContext(Dispatchers.IO) {
        try {
            if (File(localDir, ".git").exists()) return@withContext GitResult.Success
            localDir.mkdirs()
            Git.init().setDirectory(localDir).call().close()
            GitResult.Success
        } catch (e: Exception) {
            GitResult.Error("Init failed: ${e.message}", e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun credentialsProvider(username: String, token: String) =
        UsernamePasswordCredentialsProvider(username, token)
}
