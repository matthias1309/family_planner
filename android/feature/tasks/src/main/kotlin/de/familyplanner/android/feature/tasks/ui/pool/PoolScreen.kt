package de.familyplanner.android.feature.tasks.ui.pool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.tasks.R
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import org.koin.androidx.compose.koinViewModel

@Composable
fun PoolRoute(onDone: () -> Unit) {
    val viewModel = koinViewModel<PoolViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    PoolScreen(state = state, onEvent = viewModel::onEvent, onBack = onDone)
    // Nach dem Nehmen zurück zum Board; das lädt beim Zurückkehren neu.
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
}

@Composable
fun PoolScreen(
    state: PoolUiState,
    onEvent: (PoolEvent) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.tasks_pool_title), style = MaterialTheme.typography.headlineMedium) }
        item { ErrorText(state.error) }
        if (state.error != null) {
            item { TextButton(onClick = { onEvent(PoolEvent.Retry) }) { Text(stringResource(R.string.tasks_retry)) } }
        }
        item { ErrorText(state.actionError) }
        if (state.isEmpty) item { Text(stringResource(R.string.tasks_pool_empty)) }
        items(state.tasks, key = { it.id }) { task -> PoolRow(task, enabled = state.busyId == null, onEvent = onEvent) }
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.tasks_back)) } }
    }
}

@Composable
private fun PoolRow(
    task: CatalogTask,
    enabled: Boolean,
    onEvent: (PoolEvent) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(task.emoji, task.title).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.tasks_card_points, task.points), style = MaterialTheme.typography.labelLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onEvent(PoolEvent.Take(task.id, alreadyDone = false)) }, enabled = enabled) {
                    Text(stringResource(R.string.tasks_pool_take))
                }
                Button(onClick = { onEvent(PoolEvent.Take(task.id, alreadyDone = true)) }, enabled = enabled) {
                    Text(stringResource(R.string.tasks_pool_take_done))
                }
            }
        }
    }
}
