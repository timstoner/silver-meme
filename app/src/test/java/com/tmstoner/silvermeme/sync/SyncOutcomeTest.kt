package com.tmstoner.silvermeme.sync

import com.tmstoner.silvermeme.data.repository.GitRepository.GitResult
import com.tmstoner.silvermeme.sync.SyncOutcome.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncOutcomeTest {

    private val error = GitResult.Error("network down")
    private val conflict = GitResult.Conflict(listOf("Tasks/Report.md"))

    @Test
    fun `pull and push succeed`() {
        assertEquals(Action.SUCCESS, SyncOutcome.decide(GitResult.Success, GitResult.Success, 0))
    }

    @Test
    fun `failed pull with skipped push retries`() {
        assertEquals(Action.RETRY, SyncOutcome.decide(error, null, 0))
    }

    @Test
    fun `failed push retries`() {
        assertEquals(Action.RETRY, SyncOutcome.decide(GitResult.Success, error, 1))
    }

    @Test
    fun `errors stop retrying after the limit`() {
        assertEquals(Action.RETRY, SyncOutcome.decide(error, null, SyncOutcome.MAX_RETRIES - 1))
        assertEquals(Action.SUCCESS, SyncOutcome.decide(error, null, SyncOutcome.MAX_RETRIES))
    }

    @Test
    fun `conflict is left for the user instead of retried`() {
        assertEquals(Action.SUCCESS, SyncOutcome.decide(conflict, null, 0))
        assertEquals(Action.SUCCESS, SyncOutcome.decide(GitResult.Success, conflict, 0))
    }

    @Test
    fun `exceptions retry like errors`() {
        assertEquals(Action.RETRY, SyncOutcome.decideOnException(0))
        assertEquals(Action.SUCCESS, SyncOutcome.decideOnException(SyncOutcome.MAX_RETRIES))
    }
}
