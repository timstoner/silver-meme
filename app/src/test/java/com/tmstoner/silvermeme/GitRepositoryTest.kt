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

class GitRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `resolve conflicts keeps local version and pushes resolution`() = runBlocking {
        val bareRemote = temporaryFolder.newFolder("remote.git")
        val seed = temporaryFolder.newFolder("seed")
        val local = temporaryFolder.newFolder("local")
        val remoteWorkingCopy = temporaryFolder.newFolder("remote-working-copy")
        val taskPath = "Tasks/task.md"

        Git.init().setDirectory(seed).call().use { git ->
            writeTask(seed, taskPath, "base")
            commitAll(git, "base")
            Git.init().setBare(true).setDirectory(bareRemote).call().close()
            git.remoteAdd()
                .setName("origin")
                .setUri(URIish(bareRemote.toURI().toString()))
                .call()
            git.push().setRemote("origin").setPushAll().call()
        }

        Git.cloneRepository()
            .setURI(bareRemote.toURI().toString())
            .setDirectory(local)
            .call()
            .close()
        Git.cloneRepository()
            .setURI(bareRemote.toURI().toString())
            .setDirectory(remoteWorkingCopy)
            .call()
            .close()

        Git.open(local).use { git ->
            writeTask(local, taskPath, "local version")
            commitAll(git, "local change")
        }
        Git.open(remoteWorkingCopy).use { git ->
            writeTask(remoteWorkingCopy, taskPath, "remote version")
            commitAll(git, "remote change")
            git.push().setRemote("origin").setPushAll().call()
        }

        val repository = GitRepository()
        val pullResult = repository.pull(local, "user", "token")
        assertTrue(pullResult is GitRepository.GitResult.Conflict)

        val resolution = repository.resolveConflicts(
            localDir = local,
            files = listOf(taskPath),
            keepLocal = true,
            username = "user",
            token = "token",
            authorName = "Test",
            authorEmail = "test@example.com"
        )

        assertEquals(GitRepository.GitResult.Success, resolution)
        assertEquals("local version", File(local, taskPath).readText())

        Git.open(remoteWorkingCopy).use { git ->
            git.pull().call()
        }
        assertEquals("local version", File(remoteWorkingCopy, taskPath).readText())
    }

    private fun writeTask(repository: File, relativePath: String, text: String) {
        File(repository, relativePath).apply {
            parentFile?.mkdirs()
            writeText(text)
        }
    }

    private fun commitAll(git: Git, message: String) {
        git.add().addFilepattern(".").call()
        git.commit()
            .setAuthor("Test", "test@example.com")
            .setMessage(message)
            .call()
    }
}
