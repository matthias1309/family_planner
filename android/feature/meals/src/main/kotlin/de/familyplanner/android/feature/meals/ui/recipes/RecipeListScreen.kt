package de.familyplanner.android.feature.meals.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.recipe.RecipeSummary
import de.familyplanner.android.feature.meals.ui.recipe.RecipeListArgs
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RecipeListRoute(
    args: RecipeListArgs,
    onOpen: (recipeId: String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<RecipeListViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Nach Anlegen, Ändern oder Löschen hat sich die Liste evtl. geändert.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(RecipeListEvent.Refresh)
        onPauseOrDispose {}
    }
    RecipeListScreen(state = state, onEvent = viewModel::onEvent, onOpen = onOpen, onCreate = onCreate, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    state: RecipeListUiState,
    onEvent: (RecipeListEvent) -> Unit,
    onOpen: (recipeId: String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(RecipeListEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val title = if (state.pick) R.string.recipes_pick_title else R.string.recipes_title
                Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onEvent(RecipeListEvent.QueryChanged(it)) },
                    label = { Text(stringResource(R.string.recipes_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { ErrorText(state.error) }
            if (showsEmptyHint(state)) item { EmptyHint(state) }
            items(state.visible, key = { it.id }) { RecipeRow(it) { onOpen(it.id) } }
            if (state.canCreate) {
                item {
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.recipes_new)) }
                }
            }
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.recipes_back)) } }
        }
    }
}

private fun showsEmptyHint(state: RecipeListUiState) = !state.isLoading && state.error == null && state.visible.isEmpty()

@Composable
private fun EmptyHint(state: RecipeListUiState) {
    if (state.recipes.isEmpty()) {
        Text(stringResource(R.string.recipes_empty))
    } else {
        Text(stringResource(R.string.recipes_no_match, state.query.trim()))
    }
}

@Composable
private fun RecipeRow(
    recipe: RecipeSummary,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(recipe.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.recipes_servings, recipe.servings), style = MaterialTheme.typography.bodyMedium)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall)
    }
}
