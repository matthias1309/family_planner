package de.familyplanner.android.feature.tasks.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.feature.tasks.R
import org.koin.androidx.compose.koinViewModel

private val CardPadding = 16.dp

@Composable
fun TodayTasksRoute(onOpenTasks: () -> Unit) {
    val viewModel = koinViewModel<TodayTasksViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayTasksCard(state = state, onOpenTasks = onOpenTasks)
}

/** Karte „Deine Aufgaben“ (Kind) bzw. „Aufgaben“ (Eltern); ein Tipp öffnet das Board. */
@Composable
fun TodayTasksCard(
    state: TodayTasksUiState,
    onOpenTasks: () -> Unit,
) {
    Card(onClick = onOpenTasks, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(CardPadding)) {
            Text(
                stringResource(if (state.isParent) R.string.tasks_today_title_parent else R.string.tasks_today_title_child),
                style = MaterialTheme.typography.titleMedium,
            )
            when {
                state.isLoading -> Unit
                state.unavailable -> Text(stringResource(R.string.tasks_today_unavailable))
                state.isParent -> ParentSummary(state)
                else -> ChildSummary(state)
            }
        }
    }
}

@Composable
private fun ParentSummary(state: TodayTasksUiState) {
    if (state.reviewCount == 0) {
        Text(stringResource(R.string.tasks_today_review_none))
    } else {
        Text(pluralStringResource(R.plurals.tasks_today_review, state.reviewCount, state.reviewCount))
    }
}

@Composable
private fun ChildSummary(state: TodayTasksUiState) {
    if (state.openCount == 0) {
        Text(stringResource(R.string.tasks_today_all_done))
        return
    }
    Text(stringResource(R.string.tasks_today_open, state.openCount), style = MaterialTheme.typography.titleLarge)
    state.titles.forEach { Text(it) }
    if (state.rejectedCount > 0) {
        Text(stringResource(R.string.tasks_today_rejected, state.rejectedCount), color = MaterialTheme.colorScheme.error)
    }
}
