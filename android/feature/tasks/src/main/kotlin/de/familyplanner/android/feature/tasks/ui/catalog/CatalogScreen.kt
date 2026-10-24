package de.familyplanner.android.feature.tasks.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.tasks.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun CatalogRoute(
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<CatalogViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Nach dem Bearbeiten (Speichern, Archivieren) zeigt die Liste sofort den neuen Stand.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    CatalogScreen(state, onOpen, onCreate, onBack)
}

@Composable
fun CatalogScreen(
    state: CatalogUiState,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(stringResource(R.string.tasks_catalog_title), style = MaterialTheme.typography.headlineMedium) }
            state.error?.let { item { ErrorText(it) } }
            if (state.isEmpty) item { Text(stringResource(R.string.tasks_catalog_empty)) }
            items(state.tasks, key = { it.id }) { task ->
                Card(onClick = { onOpen(task.id) }) {
                    ListItem(
                        headlineContent = { Text(listOfNotNull(task.emoji, task.title).joinToString(" ")) },
                        trailingContent = { Text(stringResource(R.string.tasks_card_points, task.points)) },
                    )
                }
            }
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.tasks_back)) } }
        }
        ExtendedFloatingActionButton(
            onClick = onCreate,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.tasks_catalog_new)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}
