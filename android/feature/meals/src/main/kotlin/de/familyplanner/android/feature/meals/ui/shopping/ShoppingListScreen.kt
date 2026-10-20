package de.familyplanner.android.feature.meals.ui.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.meals.R
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ShoppingListRoute(
    args: ShoppingListArgs,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<ShoppingListViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Andere Elternteile haken ab, Gerichte werden freigegeben: beim Zurückkehren neu laden.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(ShoppingListEvent.Refresh)
        onPauseOrDispose {}
    }
    ShoppingListScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    state: ShoppingListUiState,
    onEvent: (ShoppingListEvent) -> Unit,
    onBack: () -> Unit,
) {
    val sections = state.sections
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(ShoppingListEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.shopping_title), style = MaterialTheme.typography.headlineMedium) }
            item { ShoppingWeekHeader(state, onEvent) }
            item { ErrorText(state.error) }
            item { ErrorText(state.actionError) }
            if (state.isEmpty) item { Text(stringResource(R.string.shopping_empty)) }
            items(sections.open, key = { it.key }) { ShoppingRow(it, state, onEvent) }
            if (sections.done.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.shopping_done, sections.done.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(sections.done, key = { it.key }) { ShoppingRow(it, state, onEvent) }
            }
            if (state.canEdit) item { AddItemRow(state, onEvent) }
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
        }
    }
}

@Composable
private fun ShoppingWeekHeader(
    state: ShoppingListUiState,
    onEvent: (ShoppingListEvent) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { onEvent(ShoppingListEvent.PreviousWeek) }, enabled = state.canGoPrevious) {
            val previous = stringResource(R.string.week_previous)
            Text("‹", modifier = Modifier.semantics { contentDescription = previous })
        }
        Text(
            stringResource(R.string.week_title, formatDate(state.weekStart), formatDate(state.weekStart.plusDays(6))),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onEvent(ShoppingListEvent.NextWeek) }, enabled = state.canGoNext) {
            val next = stringResource(R.string.week_next)
            Text("›", modifier = Modifier.semantics { contentDescription = next })
        }
    }
}

@Composable
private fun AddItemRow(
    state: ShoppingListUiState,
    onEvent: (ShoppingListEvent) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.newItem,
            onValueChange = { onEvent(ShoppingListEvent.NewItemChanged(it)) },
            label = { Text(stringResource(R.string.shopping_add_label)) },
            placeholder = { Text(stringResource(R.string.shopping_add_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(ShoppingListEvent.AddItem) }),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onEvent(ShoppingListEvent.AddItem) },
            enabled = !state.isBusy && state.newItem.isNotBlank(),
        ) {
            Text(stringResource(R.string.shopping_add))
        }
    }
}
