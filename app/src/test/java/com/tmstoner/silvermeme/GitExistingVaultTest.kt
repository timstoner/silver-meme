package com.tmstoner.silvermeme

import com.tmstoner.silvermeme.data.repository.GitRepository
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GitExistingVaultTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `connecting populated local vault merges both sides and reports collisions`() = runBlocking {
        val remote = temporaryFolder.newFolder("remote.git")
        val seed = temporaryFolder.newFolder("seed")
        val local = temporaryFolder.newFolder("local")
        val remoteCopy = temporaryFolder.newFolder("remote-copy")
        val conflictPath = "Tasks/collision.md"

        Git.init().setBare(true).setDirectory(remote).call().close()
        Git.init().setDirectory(seed).call().use { git ->
            write(seed, conflictPath, "remote version")
            write(seed, "Tasks/remote-only.md", "remote only")
            git.add().addFilepattern(".").call()
            git.commit().setAuthor("Test", "test@example.com").setMessage("seed").call()
            git.remoteAdd().setName("origin").setUri(URIish(remote.toURI().toString())).call()
            git.push().setRemote("origin").setPushAll().call()
        }

        write(local, conflictPath, "local version")
        write(local, "Tasks/local-only.md", "local only")

        val repository = GitRepository()
        val result = repository.cloneOrPull(
            remoteUrl = remote.toURI().toString(),
            localDir = local,
            username = "",
            token = ""
        )

        assertTrue("Expected explicit conflict, got $result", result is GitRepository.GitResult.Conflict)
        assertEquals(listOf(conflictPath), (result as GitRepository.GitResult.Conflict).files)
        assertEquals("local only", File(local, "Tasks/local-only.md").readText())
        assertEquals("remote only", File(local, "Tasks/remote-only.md").readText())
        Git.open(local).use { git ->
            val cache = git.repository.readDirCache()
            val stages = (0 until cache.entryCount).map(cache::getEntry)
                .filter { it.pathString == conflictPath }
                .map { it.stage }
                .toSet()
            assertEquals(setOf(1, 2, 3), stages)
        }

        val resolution = repository.resolveConflicts(
            localDir = local,
            files = listOf(conflictPath),
            keepLocal = true,
            username = "",
            token = "",
            authorName = "Test",
            authorEmail = "test@example.com"
        )
        assertEquals(GitRepository.GitResult.Success, resolution)
        assertEquals("local version", File(local, conflictPath).readText())

        Git.cloneRepository().setURI(remote.toURI().toString()).setDirectory(remoteCopy).call().close()
        assertEquals("local version", File(remoteCopy, conflictPath).readText())
        assertEquals("remote only", File(remoteCopy, "Tasks/remote-only.md").readText())
    }

    @Test
    fun `populated vault conflict can be resolved by keeping the remote filename version`() = runBlocking {
        val remote = temporaryFolder.newFolder("remote-keep-remote.git")
        val seed = temporaryFolder.newFolder("seed-keep-remote")
        val local = temporaryFolder.newFolder("local-keep-remote")
        val path = "Tasks/shared-name.md"

        Git.init().setBare(true).setDirectory(remote).call().close()
        Git.init().setDirectory(seed).call().use { git ->
            write(seed, path, "remote file")
            git.add().addFilepattern(".").call()
            git.commit().setAuthor("Test", "test@example.com").setMessage("seed").call()
            git.remoteAdd().setName("origin").setUri(URIish(remote.toURI().toString())).call()
            git.push().setRemote("origin").setPushAll().call()
        }
        write(local, path, "local file with same name")

        val repository = GitRepository()
        val attach = repository.cloneOrPull(
            remoteUrl = remote.toURI().toString(),
            localDir = local,
            username = "",
            token = ""
        )
        assertTrue("Expected filename conflict, got $attach", attach is GitRepository.GitResult.Conflict)
        assertEquals(
            GitRepository.GitResult.Success,
            repository.resolveConflicts(
                localDir = local,
                files = listOf(path),
                keepLocal = false,
                username = "",
                token = "",
                authorName = "Test",
                authorEmail = "test@example.com"
            )
        )

        assertEquals("remote file", File(local, path).readText())
    }

    private fun write(root: File, path: String, value: String) {
        File(root, path).apply {
            parentFile?.mkdirs()
            writeText(value)
        }
    }
}
