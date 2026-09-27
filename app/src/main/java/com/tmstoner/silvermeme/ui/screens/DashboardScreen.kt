package com.tmstoner.silvermeme.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.ui.theme.SilvermemeTheme
import com.tmstoner.silvermeme.viewmodel.DashboardSummary
import com.tmstoner.silvermeme.viewmodel.TodoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: TodoViewModel,
    onOpenNavigationDrawer: () -> Unit
) {
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()

    DashboardContent(
        summary = summary,
        onOpenNavigationDrawer = onOpenNavigationDrawer
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    summary: DashboardSummary,
    onOpenNavigationDrawer: () -> Unit
) {
    val summaryItems = listOf(
        R.string.dashboard_open_tasks to summary.openTasks,
        R.string.dashboard_due_today to summary.dueToday,
        R.string.dashboard_overdue to summary.overdue,
        R.string.dashboard_completed to summary.completed
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.destination_dashboard)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onOpenNavigationDrawer) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.cd_open_navigation)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_summary_heading),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            items(summaryItems, key = { it.first }) { (label, count) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardContentPreview() {
    SilvermemeTheme(dynamicColor = false) {
        DashboardContent(
            summary = DashboardSummary(
                openTasks = 8,
                dueToday = 2,
                overdue = 1,
                completed = 5
            ),
            onOpenNavigationDrawer = {}
        )
    }
}
