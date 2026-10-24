package de.familyplanner.android.feature.tasks.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatDateTime
import de.familyplanner.android.feature.tasks.R
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun HistoryRoute(
    args: HistoryArgs,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<HistoryViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(state, onBack)
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(stringResource(R.string.tasks_history_title, state.name), style = MaterialTheme.typography.headlineMedium) }
        state.error?.let { item { ErrorText(it) } }
        if (state.isEmpty) item { Text(stringResource(R.string.tasks_history_empty)) }
        items(state.cards, key = { it.id }) { card ->
            ListItem(
                headlineContent = { Text(listOfNotNull(card.emoji, card.title).joinToString(" ")) },
                supportingContent = { Text(stringResource(R.string.tasks_history_done, card.doneAt?.let(::formatDateTime).orEmpty())) },
                trailingContent = { Text(stringResource(R.string.tasks_card_points, card.points)) },
            )
        }
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.tasks_back)) } }
    }
}
