package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.viewmodel.SyncState

/** Presents safely retryable synchronization errors as an actionable snackbar. */
@Composable
fun SyncFailureFeedback(
    syncState: SyncState,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val retryLabel = stringResource(R.string.action_retry)
    val errorPrefix = stringResource(R.string.error_sync_prefix)

    LaunchedEffect(syncState) {
        val failure = syncState as? SyncState.Failure ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = errorPrefix.format(failure.message),
            actionLabel = retryLabel,
            withDismissAction = true
        )
        if (result == SnackbarResult.ActionPerformed) onRetry() else onDismiss()
    }
}

@Composable
fun PendingSyncBanner(
    pending: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!pending) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.pending_sync_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
