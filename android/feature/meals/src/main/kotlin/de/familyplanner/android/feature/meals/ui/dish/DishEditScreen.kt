package de.familyplanner.android.feature.meals.ui.dish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ConfirmDialog
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.components.SubmitButton
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.ui.WeekDays
import de.familyplanner.android.feature.meals.ui.weekdayShort
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate

private const val INGREDIENT_MIN_LINES = 4

@Composable
fun DishEditRoute(
    date: LocalDate,
    dishId: String?,
    onDone: () -> Unit,
) {
    val viewModel = koinViewModel<DishEditViewModel> { parametersOf(date, dishId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DishEditScreen(state = state, onEvent = viewModel::onEvent, onBack = onDone)
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
}

@Composable
fun DishEditScreen(
    state: DishEditUiState,
    onEvent: (DishEditEvent) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Text(
            stringResource(if (state.isNew) R.string.dish_title_new else R.string.dish_title_edit),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (state.loadError != null) {
            ErrorText(state.loadError)
            OutlinedButton(onClick = { onEvent(DishEditEvent.Retry) }) { Text(stringResource(R.string.dish_retry)) }
        } else if (!state.isLoading) {
            DayChips(state, onEvent)
            CookPicker(state, onEvent)
            DishFields(state, onEvent)
            ErrorText(state.error)
            SubmitButton(
                text = stringResource(R.string.dish_save),
                enabled = state.canSave,
                loading = state.isBusy,
                onClick = { onEvent(DishEditEvent.Save) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!state.isNew) {
                OutlinedButton(
                    onClick = { onEvent(DishEditEvent.RequestDelete) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.dish_delete)) }
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.dish_cancel)) }
    }
    if (state.showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.dish_delete_title),
            text = stringResource(R.string.dish_delete_text),
            confirmLabel = stringResource(R.string.dish_delete_confirm),
            onConfirm = { onEvent(DishEditEvent.ConfirmDelete) },
            onDismiss = { onEvent(DishEditEvent.CancelDelete) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(
    state: DishEditUiState,
    onEvent: (DishEditEvent) -> Unit,
) {
    Column {
        Text(stringResource(R.string.dish_day), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeekDays.map { state.weekStart.plusDays(it.ordinal.toLong()) }.forEach { day ->
                val taken = day in state.takenDates
                val label = "${weekdayShort(day.dayOfWeek)} ${formatDate(day)}"
                val description = if (taken) stringResource(R.string.dish_day_taken, label) else label
                FilterChip(
                    selected = day == state.date,
                    onClick = { onEvent(DishEditEvent.DateSelected(day)) },
                    enabled = !taken,
                    label = { Text(weekdayShort(day.dayOfWeek)) },
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }
    }
}

@Composable
private fun CookPicker(
    state: DishEditUiState,
    onEvent: (DishEditEvent) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = state.parents.firstOrNull { it.id == state.cookId }?.name ?: stringResource(R.string.dish_cook_pick)
    Column {
        Text(stringResource(R.string.dish_cook), style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.parents.forEach { parent ->
                    DropdownMenuItem(
                        text = { Text(parent.name) },
                        onClick = {
                            expanded = false
                            onEvent(DishEditEvent.CookSelected(parent.id))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DishFields(
    state: DishEditUiState,
    onEvent: (DishEditEvent) -> Unit,
) {
    OutlinedTextField(
        value = state.name,
        onValueChange = { onEvent(DishEditEvent.NameChanged(it)) },
        label = { Text(stringResource(R.string.dish_name)) },
        singleLine = true,
        supportingText = { Text("${state.name.length}/${PlanLimits.NAME_MAX}") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.ingredientsText,
        onValueChange = { onEvent(DishEditEvent.IngredientsChanged(it)) },
        label = { Text(stringResource(R.string.dish_ingredients)) },
        singleLine = false,
        minLines = INGREDIENT_MIN_LINES,
        modifier = Modifier.fillMaxWidth(),
    )
}
