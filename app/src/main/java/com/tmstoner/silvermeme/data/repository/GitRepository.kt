package com.tmstoner.silvermeme.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.TreeWalk
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
interface GitOperations {
    suspend fun cloneOrPull(
        remoteUrl: String,
        localDir: File,
        username: String,
        token: String,
        authorName: String = "SilverMeme",
        authorEmail: String = "silvermeme@local"
    ): GitRepository.GitResult

    suspend fun pull(localDir: File, username: String, token: String): GitRepository.GitResult
    suspend fun commitAndPush(
        localDir: File,
        username: String,
        token: String,
        message: String = "Update todos",
        authorName: String = "SilverMeme",
        authorEmail: String = "silvermeme@local"
    ): GitRepository.GitResult

    suspend fun resolveConflicts(
        localDir: File,
        files: List<String>,
        keepLocal: Boolean,
        username: String,
        token: String,
        authorName: String,
        authorEmail: String
    ): GitRepository.GitResult

    suspend fun initLocal(localDir: File): GitRepository.GitResult
}

class GitRepository : GitOperations {

    sealed class GitResult {
        object Success : GitResult()
        data class Error(val message: String, val cause: Throwable? = null) : GitResult()
        data class Conflict(val files: List<String>) : GitResult()
    }

    // ── Clone ─────────────────────────────────────────────────────────────────

    /**
     * Clones [remoteUrl] into [localDir].
     * If [localDir] already contains a `.git` directory, the clone is skipped and
     * a pull is performed instead.
     */
    override suspend fun cloneOrPull(
        remoteUrl: String,
        localDir: File,
        username: String,
        token: String,
        authorName: String,
        authorEmail: String
    ): GitResult = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider(username, token)
        try {
            if (File(localDir, ".git").exists()) {
                pull(localDir, credentials)
            } else if (containsVaultFiles(localDir)) {
                connectExistingVault(
                    remoteUrl, localDir, credentials, authorName, authorEmail
                )
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
            GitResult.Error(safeError("Git clone/pull failed", e, username, token, remoteUrl))
        } catch (e: Exception) {
            GitResult.Error(safeError("Unexpected error", e, username, token, remoteUrl))
        }
    }

    /**
     * Turns a populated local-only vault into the local side of a merge instead
     * of cloning over it. Distinct local/remote paths are retained; same-path
     * edits are left as Git conflicts for the user to resolve explicitly.
     */
    private fun connectExistingVault(
        remoteUrl: String,
        localDir: File,
        credentials: UsernamePasswordCredentialsProvider,
        authorName: String,
        authorEmail: String
    ): GitResult {
        val localFiles = if (localDir.exists()) {
            localDir.walkTopDown()
                .filter { it.isFile }
                .filter { it.relativeTo(localDir).path.split(File.separatorChar).firstOrNull() != ".git" }
                .associate { it.relativeTo(localDir).path.replace(File.separatorChar, '/') to it.readBytes() }
        } else {
            emptyMap()
        }
        localDir.mkdirs()
        Git.init().setDirectory(localDir).call().use { git ->
            git.add().addFilepattern(".").call()
            git.commit()
                .setAllowEmpty(true)
                .setAuthor(authorName, authorEmail)
                .setCommitter(authorName, authorEmail)
                .setMessage("Import existing local vault")
                .call()
            git.remoteAdd()
                .setName("origin")
                .setUri(org.eclipse.jgit.transport.URIish(remoteUrl))
                .call()
            val fetchResult = git.fetch()
                .setRemote("origin")
                .setCredentialsProvider(credentials)
                .call()

            val remoteHead = git.repository.resolve(Constants.FETCH_HEAD)
            if (remoteHead == null) {
                val branch = git.repository.fullBranch?.removePrefix(Constants.R_HEADS) ?: Constants.MASTER
                git.repository.config.apply {
                    setString("branch", branch, "remote", "origin")
                    setString("branch", branch, "merge", "${Constants.R_HEADS}$branch")
                    save()
                }
                return GitResult.Success // An empty remote has nothing to merge.
            }
            val advertisedHead = fetchResult.getAdvertisedRef(Constants.HEAD)
            val upstream = advertisedHead?.target?.name
                ?: advertisedHead?.name?.takeIf { it.startsWith(Constants.R_HEADS) }
            val upstreamBranch = upstream?.removePrefix(Constants.R_HEADS)
            val initialBranch = git.repository.fullBranch?.removePrefix(Constants.R_HEADS)
            if (!upstreamBranch.isNullOrBlank() && upstreamBranch != initialBranch) {
                git.branchRename().setNewName(upstreamBranch).call()
            }
            // The installed JGit does not expose git-merge's unrelated-history
            // option. Make the remote tree the new base, then layer local files
            // over it, creating explicit index conflicts for path collisions.
            git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD)
                .setRef(Constants.FETCH_HEAD)
                .call()
            val currentBranch = git.repository.fullBranch
                ?.removePrefix(Constants.R_HEADS)
                ?: Constants.MASTER
            git.repository.config.apply {
                setString("branch", currentBranch, "remote", "origin")
                setString("branch", currentBranch, "merge", upstream ?: "${Constants.R_HEADS}$currentBranch")
                save()
            }

            val remoteTree = RevWalk(git.repository).use { it.parseCommit(remoteHead).tree }
            val conflicts = mutableListOf<Pair<String, ByteArray>>()
            localFiles.forEach { (path, bytes) ->
                val remotePath = TreeWalk.forPath(git.repository, path, remoteTree)
                if (remotePath == null) {
                    writeVaultFile(localDir, path, bytes)
                    git.add().addFilepattern(path).call()
                } else {
                    val remoteId = remotePath.getObjectId(0)
                    val remoteBytes = git.repository.open(remoteId).bytes
                    if (!bytes.contentEquals(remoteBytes)) conflicts += path to bytes
                    remotePath.close()
                }
            }
            if (conflicts.isEmpty()) return GitResult.Success

            val cache = git.repository.lockDirCache()
            val builder = cache.builder()
            val paths = conflicts.map { it.first }.toSet()
            for (index in 0 until cache.entryCount) {
                val entry = cache.getEntry(index)
                if (entry.pathString !in paths) builder.add(entry)
            }
            val inserter = git.repository.newObjectInserter()
            conflicts.forEach { (path, localBytes) ->
                val remotePath = TreeWalk.forPath(git.repository, path, remoteTree)
                    ?: return GitResult.Error("Remote conflict file disappeared: $path")
                val remoteId = remotePath.getObjectId(0)
                val localId = inserter.insert(Constants.OBJ_BLOB, localBytes)
                remotePath.close()
                listOf(1 to remoteId, 2 to localId, 3 to remoteId).forEach { (stage, objectId) ->
                    builder.add(
                        DirCacheEntry(path, stage).apply {
                            setFileMode(FileMode.REGULAR_FILE)
                            setObjectId(objectId)
                        }
                    )
                }
                writeVaultFile(localDir, path, localBytes)
            }
            inserter.flush()
            inserter.close()
            builder.finish()
            cache.write()
            cache.commit()
            return GitResult.Conflict(conflicts.map { it.first })
        }
    }

    private fun writeVaultFile(localDir: File, path: String, bytes: ByteArray) {
        val target = File(localDir, path)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    private fun containsVaultFiles(directory: File): Boolean =
        directory.exists() && directory.walkTopDown().any { file ->
            file.isFile && file.relativeTo(directory).path
                .split(File.separatorChar)
                .firstOrNull() != ".git"
        }

    // ── Pull ──────────────────────────────────────────────────────────────────

    /**
     * Pulls the latest changes from the configured remote.
     * Reports files with merge conflicts without discarding local changes.
     */
    override suspend fun pull(
        localDir: File,
        username: String,
        token: String
    ): GitResult = withContext(Dispatchers.IO) {
        try {
            pull(localDir, credentialsProvider(username, token))
        } catch (e: Exception) {
            GitResult.Error(safeError("Pull failed", e, username, token))
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
                val status = git.status().call()
                val conflictingFiles = status.conflicting.toList()
                if (conflictingFiles.isNotEmpty()) {
                    GitResult.Conflict(conflictingFiles)
                } else {
                    GitResult.Error("Pull completed with conflicts")
                }
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
    override suspend fun commitAndPush(
        localDir: File,
        username: String,
        token: String,
        message: String,
        authorName: String,
        authorEmail: String
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
            GitResult.Error(safeError("Commit/push failed", e, username, token))
        } catch (e: Exception) {
            GitResult.Error(safeError("Unexpected error during commit/push", e, username, token))
        }
    }

    override suspend fun resolveConflicts(
        localDir: File,
        files: List<String>,
        keepLocal: Boolean,
        username: String,
        token: String,
        authorName: String,
        authorEmail: String
    ): GitResult = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider(username, token)
        try {
            Git.open(localDir).use { git ->
                val conflictedPaths = git.status().call().conflicting
                val selectedPaths = files.distinct()
                if (selectedPaths.isEmpty() || selectedPaths.any { it !in conflictedPaths }) {
                    return@withContext GitResult.Error("The conflict list changed. Sync again to refresh it.")
                }

                val rootPath = localDir.canonicalPath + File.separator
                for (path in selectedPaths) {
                    if (path.startsWith("/") || path.split('/').any { it == ".." || it == "." }) {
                        return@withContext GitResult.Error("Invalid conflict path: $path")
                    }
                    val file = File(localDir, path)
                    if (!file.canonicalPath.startsWith(rootPath)) {
                        return@withContext GitResult.Error("Invalid conflict path: $path")
                    }
                }

                selectedPaths.forEach { path ->
                    git.checkout()
                        .addPath(path)
                        .setStage(
                            if (keepLocal) CheckoutCommand.Stage.OURS else CheckoutCommand.Stage.THEIRS
                        )
                        .call()
                    val resolvedFile = File(localDir, path)
                    if (resolvedFile.exists()) {
                        git.add().addFilepattern(path).call()
                    } else {
                        git.rm().addFilepattern(path).call()
                    }
                }

                val remainingConflicts = git.status().call().conflicting
                if (remainingConflicts.isNotEmpty()) {
                    return@withContext GitResult.Conflict(remainingConflicts.toList())
                }
                git.commit()
                    .setAuthor(authorName, authorEmail)
                    .setCommitter(authorName, authorEmail)
                    .setMessage("Resolve sync conflicts")
                    .call()
                git.push().setCredentialsProvider(credentials).call()
            }
            GitResult.Success
        } catch (e: GitAPIException) {
            GitResult.Error(safeError("Conflict resolution failed", e, username, token))
        } catch (e: Exception) {
            GitResult.Error(safeError("Unexpected error during conflict resolution", e, username, token))
        }
    }

    // ── Init (for local-only use without a remote) ────────────────────────────

    /**
     * Initialises a brand-new local git repository at [localDir].
     * Useful when the user wants to work locally without a remote.
     */
    override suspend fun initLocal(localDir: File): GitResult = withContext(Dispatchers.IO) {
        try {
            if (File(localDir, ".git").exists()) return@withContext GitResult.Success
            localDir.mkdirs()
            Git.init().setDirectory(localDir).call().close()
            GitResult.Success
        } catch (e: Exception) {
            GitResult.Error("Init failed: ${e.message ?: "unknown error"}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun credentialsProvider(username: String, token: String) =
        UsernamePasswordCredentialsProvider(username, token)

    /** Keep credentials out of error messages and Throwable values exposed to callers. */
    private fun safeError(
        prefix: String,
        error: Throwable,
        username: String,
        token: String,
        remoteUrl: String = ""
    ): String {
        var detail = error.message ?: "unknown error"
        listOf(token, username, remoteUrl).filter { it.isNotBlank() }.forEach { secret ->
            detail = detail.replace(secret, "[redacted]", ignoreCase = false)
        }
        detail = detail.replace(
            Regex("(?i)(https?://)[^/@\\s]+@"),
            "$1[redacted]@"
        )
        return "$prefix: $detail"
    }
}
