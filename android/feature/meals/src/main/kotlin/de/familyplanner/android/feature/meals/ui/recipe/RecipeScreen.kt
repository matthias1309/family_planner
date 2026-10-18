package de.familyplanner.android.feature.meals.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ConfirmDialog
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.recipe.Recipe
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RecipeViewRoute(
    args: RecipeViewArgs,
    onEdit: (recipeId: String) -> Unit,
    onDone: () -> Unit,
) {
    val viewModel = koinViewModel<RecipeViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Nach dem Bearbeiten hat sich das Rezept evtl. geändert.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(RecipeEvent.Refresh)
        onPauseOrDispose {}
    }
    RecipeScreen(state = state, onEvent = viewModel::onEvent, onEdit = onEdit, onBack = onDone)
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onDone() }
}

@Composable
fun RecipeScreen(
    state: RecipeUiState,
    onEvent: (RecipeEvent) -> Unit,
    onEdit: (recipeId: String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        val recipe = state.recipe
        if (recipe != null) {
            RecipeBody(recipe, state)
            if (state.canManage) ManageButtons(recipe, state, onEvent, onEdit)
        } else if (state.error != null) {
            ErrorText(state.error)
            OutlinedButton(onClick = { onEvent(RecipeEvent.Refresh) }) { Text(stringResource(R.string.recipe_retry)) }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
    if (state.showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.recipe_delete_title),
            text = stringResource(R.string.recipe_delete_text),
            confirmLabel = stringResource(R.string.recipe_delete_confirm),
            onConfirm = { onEvent(RecipeEvent.ConfirmDelete) },
            onDismiss = { onEvent(RecipeEvent.CancelDelete) },
        )
    }
}

@Composable
private fun RecipeBody(
    recipe: Recipe,
    state: RecipeUiState,
) {
    Text(recipe.name, style = MaterialTheme.typography.headlineMedium)
    state.servings?.let { Text(stringResource(R.string.recipe_servings, it), style = MaterialTheme.typography.titleMedium) }
    Text(stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.titleSmall)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        state.ingredientLines.forEach { Text("• $it") }
    }
    Text(stringResource(R.string.recipe_steps), style = MaterialTheme.typography.titleSmall)
    if (recipe.steps.isEmpty()) {
        Text(stringResource(R.string.recipe_no_steps))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recipe.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
        }
    }
    recipe.sourceUrl?.let { Text(stringResource(R.string.recipe_source, it), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ManageButtons(
    recipe: Recipe,
    state: RecipeUiState,
    onEvent: (RecipeEvent) -> Unit,
    onEdit: (recipeId: String) -> Unit,
) {
    ErrorText(state.actionError)
    Button(onClick = { onEdit(recipe.id) }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.recipe_edit))
    }
    OutlinedButton(onClick = { onEvent(RecipeEvent.RequestDelete) }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.recipe_delete))
    }
}
