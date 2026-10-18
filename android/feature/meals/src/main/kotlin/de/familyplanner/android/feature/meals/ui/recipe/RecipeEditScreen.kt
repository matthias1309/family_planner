package de.familyplanner.android.feature.meals.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.components.SubmitButton
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private const val TEXT_MIN_LINES = 4

@Composable
fun RecipeEditRoute(
    args: RecipeEditArgs,
    onDone: () -> Unit,
) {
    val viewModel = koinViewModel<RecipeEditViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecipeEditScreen(state = state, onEvent = viewModel::onEvent, onBack = onDone)
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
}

@Composable
fun RecipeEditScreen(
    state: RecipeEditUiState,
    onEvent: (RecipeEditEvent) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Text(
            stringResource(if (state.isNew) R.string.recipe_title_new else R.string.recipe_title_edit),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (state.loadError != null) {
            ErrorText(state.loadError)
            if (state.recipeId != null) {
                OutlinedButton(onClick = { onEvent(RecipeEditEvent.Retry) }) { Text(stringResource(R.string.recipe_retry)) }
            }
        } else if (!state.isLoading) {
            RecipeFields(state, onEvent)
            ErrorText(state.error)
            SubmitButton(
                text = stringResource(R.string.recipe_save),
                enabled = state.canSave,
                loading = state.isBusy,
                onClick = { onEvent(RecipeEditEvent.Save) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.recipe_cancel)) }
    }
}

@Composable
private fun RecipeFields(
    state: RecipeEditUiState,
    onEvent: (RecipeEditEvent) -> Unit,
) {
    OutlinedTextField(
        value = state.name,
        onValueChange = { onEvent(RecipeEditEvent.NameChanged(it)) },
        label = { Text(stringResource(R.string.recipe_name)) },
        singleLine = true,
        supportingText = { Text("${state.name.length}/${PlanLimits.NAME_MAX}") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.recipe_servings_label), style = MaterialTheme.typography.titleSmall)
        ServingsStepper(value = state.servings, onChange = { onEvent(RecipeEditEvent.ServingsChanged(it)) }, enabled = !state.isBusy)
    }
    OutlinedTextField(
        value = state.ingredientsText,
        onValueChange = { onEvent(RecipeEditEvent.IngredientsChanged(it)) },
        label = { Text(stringResource(R.string.recipe_ingredients_label)) },
        minLines = TEXT_MIN_LINES,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.stepsText,
        onValueChange = { onEvent(RecipeEditEvent.StepsChanged(it)) },
        label = { Text(stringResource(R.string.recipe_steps_label)) },
        minLines = TEXT_MIN_LINES,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.sourceUrl,
        onValueChange = { onEvent(RecipeEditEvent.SourceUrlChanged(it)) },
        label = { Text(stringResource(R.string.recipe_source_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
}
