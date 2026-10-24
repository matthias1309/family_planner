package de.familyplanner.android.feature.tasks.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ConfirmDialog
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.components.SubmitButton
import de.familyplanner.android.feature.tasks.R
import de.familyplanner.android.feature.tasks.domain.board.TaskLimits
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CatalogEditRoute(
    args: CatalogEditArgs,
    onDone: () -> Unit,
) {
    val viewModel = koinViewModel<CatalogEditViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatalogEditScreen(state = state, onEvent = viewModel::onEvent, onBack = onDone)
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
}

@Composable
fun CatalogEditScreen(
    state: CatalogEditUiState,
    onEvent: (CatalogEditEvent) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Text(
            stringResource(if (state.isNew) R.string.tasks_catalog_edit_new else R.string.tasks_catalog_edit_existing),
            style = MaterialTheme.typography.headlineMedium,
        )
        ErrorText(state.loadError)
        if (state.loadError !=
            null
        ) {
            TextButton(onClick = { onEvent(CatalogEditEvent.Retry) }) { Text(stringResource(R.string.tasks_retry)) }
        }
        OutlinedTextField(
            value = state.title,
            onValueChange = { onEvent(CatalogEditEvent.TitleChanged(it)) },
            label = { Text(stringResource(R.string.tasks_field_title)) },
            supportingText = { Text("${state.title.length}/${TaskLimits.TITLE_MAX}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.emoji,
            onValueChange = { onEvent(CatalogEditEvent.EmojiChanged(it)) },
            label = { Text(stringResource(R.string.tasks_field_emoji)) },
            placeholder = { Text(stringResource(R.string.tasks_field_emoji_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.tasks_field_points), style = MaterialTheme.typography.titleMedium)
        PointsStepper(state.points, { onEvent(CatalogEditEvent.PointsChanged(it)) }, enabled = !state.isBusy)
        ErrorText(state.error)
        SubmitButton(
            text = stringResource(R.string.tasks_save),
            enabled = state.canSave,
            loading = state.isBusy,
            onClick = { onEvent(CatalogEditEvent.Save) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.isNew) {
            OutlinedButton(
                onClick = { onEvent(CatalogEditEvent.AskArchive) },
                enabled = !state.isBusy && state.loadError == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tasks_archive)) }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.tasks_back)) }
    }
    if (state.confirmingArchive) {
        ConfirmDialog(
            title = stringResource(R.string.tasks_archive_title),
            text = stringResource(R.string.tasks_archive_text),
            confirmLabel = stringResource(R.string.tasks_archive),
            onConfirm = { onEvent(CatalogEditEvent.ConfirmArchive) },
            onDismiss = { onEvent(CatalogEditEvent.DismissArchive) },
        )
    }
}
