package de.familyplanner.android.feature.tasks.ui.assign

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import de.familyplanner.android.core.ui.components.SubmitButton
import de.familyplanner.android.feature.tasks.R
import de.familyplanner.android.feature.tasks.domain.board.TaskLimits
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssignRoute(
    args: AssignArgs,
    onOpenCatalog: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel = koinViewModel<AssignViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    AssignScreen(state = state, onEvent = viewModel::onEvent, onOpenCatalog = onOpenCatalog, onBack = onDone)
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
}

@Composable
fun AssignScreen(
    state: AssignUiState,
    onEvent: (AssignEvent) -> Unit,
    onOpenCatalog: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.tasks_assign_title), style = MaterialTheme.typography.headlineMedium) }
        item { ChildChips(state, onEvent) }
        item { ErrorText(state.error) }
        if (state.catalogEmpty) {
            item { Text(stringResource(R.string.tasks_assign_empty)) }
            item { OutlinedButton(onClick = onOpenCatalog) { Text(stringResource(R.string.tasks_assign_open_catalog)) } }
        }
        items(state.catalog, key = { it.id }) { task ->
            CatalogRow(task, checked = task.id in state.selected) { onEvent(AssignEvent.Toggle(task.id)) }
        }
        if (state.tooMany) {
            item {
                Text(stringResource(R.string.tasks_assign_too_many, TaskLimits.ASSIGN_MAX), color = MaterialTheme.colorScheme.error)
            }
        }
        item { ErrorText(state.submitError) }
        item {
            SubmitButton(
                text = stringResource(R.string.tasks_assign_submit, state.selected.size),
                enabled = state.canSubmit,
                loading = state.isBusy,
                onClick = { onEvent(AssignEvent.Submit) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.tasks_back)) } }
    }
}

@Composable
private fun ChildChips(
    state: AssignUiState,
    onEvent: (AssignEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.tasks_assign_child), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            state.children.forEach { child ->
                FilterChip(
                    selected = child.id == state.memberId,
                    onClick = { onEvent(AssignEvent.SelectChild(child.id)) },
                    label = { Text(child.name) },
                )
            }
        }
    }
}

@Composable
private fun CatalogRow(
    task: CatalogTask,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(listOfNotNull(task.emoji, task.title).joinToString(" "), modifier = Modifier.weight(1f))
        Text(stringResource(R.string.tasks_card_points, task.points), style = MaterialTheme.typography.labelLarge)
    }
}
