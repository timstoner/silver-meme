package com.tmstoner.silvermeme.sync

import com.tmstoner.silvermeme.data.repository.GitRepository

/**
 * Pure mapping from a background sync's git results to what WorkManager should do
 * next. Kept separate from [SyncWorker] so it can be unit-tested on the JVM.
 */
object SyncOutcome {

    enum class Action { SUCCESS, RETRY }

    /** Retries per period before giving up until the next scheduled run. */
    const val MAX_RETRIES = 3

    /**
     * @param pull result of the pull step
     * @param push result of the push step, or null when it was skipped
     * @param runAttemptCount WorkManager's attempt counter for this run (0 on the first try)
     */
    fun decide(
        pull: GitRepository.GitResult,
        push: GitRepository.GitResult?,
        runAttemptCount: Int
    ): Action = when {
        // A conflict needs the user (Keep local / Keep remote in the app); retrying
        // in the background won't resolve it. The next manual sync surfaces it.
        pull is GitRepository.GitResult.Conflict || push is GitRepository.GitResult.Conflict -> Action.SUCCESS
        pull is GitRepository.GitResult.Error || push is GitRepository.GitResult.Error ->
            retryOrGiveUp(runAttemptCount)
        else -> Action.SUCCESS
    }

    /** Unexpected exceptions are treated like a transient git error. */
    fun decideOnException(runAttemptCount: Int): Action = retryOrGiveUp(runAttemptCount)

    private fun retryOrGiveUp(runAttemptCount: Int): Action =
        if (runAttemptCount < MAX_RETRIES) Action.RETRY else Action.SUCCESS
}
