package de.familyplanner.android.feature.meals.ui.dish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ConfirmDialog
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.components.SubmitButton
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.plan.EditorMode
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.ui.WeekDays
import de.familyplanner.android.feature.meals.ui.plan.ReasonDialog
import de.familyplanner.android.feature.meals.ui.weekdayShort
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private const val INGREDIENT_MIN_LINES = 4

@Composable
fun DishEditRoute(
    args: DishEditArgs,
    onDone: () -> Unit,
) {
    val viewModel = koinViewModel<DishEditViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DishEditScreen(state = state, onEvent = viewModel::onEvent, onBack = onDone)
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
}

@Composable
private fun titleOf(state: DishEditUiState): String =
    stringResource(
        when (state.mode) {
            EditorMode.PARENT_REVIEW -> R.string.review_title
            EditorMode.PARENT_MOVE -> R.string.move_title
            EditorMode.CHILD -> if (state.isNew) R.string.child_title_new else R.string.child_title_edit
            EditorMode.PARENT_EDIT -> if (state.isNew) R.string.dish_title_new else R.string.dish_title_edit
        },
    )

@Composable
private fun saveLabelOf(state: DishEditUiState): String =
    stringResource(
        when {
            state.mode == EditorMode.PARENT_MOVE -> R.string.dish_move
            state.mode == EditorMode.CHILD -> if (state.isNew) R.string.child_submit else R.string.child_resubmit
            else -> R.string.dish_save
        },
    )

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
        Text(titleOf(state), style = MaterialTheme.typography.headlineMedium)
        if (state.loadError != null) {
            ErrorText(state.loadError)
            OutlinedButton(onClick = { onEvent(DishEditEvent.Retry) }) { Text(stringResource(R.string.dish_retry)) }
        } else if (!state.isLoading) {
            EditorBody(state, onEvent)
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.dish_cancel)) }
    }
    EditorDialogs(state, onEvent)
}

@Composable
private fun EditorBody(
    state: DishEditUiState,
    onEvent: (DishEditEvent) -> Unit,
) {
    when (state.mode) {
        EditorMode.PARENT_REVIEW -> ReviewSection(state, onEvent)
        EditorMode.PARENT_MOVE -> MoveBanner(state)
        EditorMode.CHILD -> ChildHints(state)
        EditorMode.PARENT_EDIT -> Unit
    }
    if (state.childCook != null && state.allowedDates.isEmpty()) {
        Text(stringResource(R.string.dish_no_allowed_day), color = MaterialTheme.colorScheme.error)
    }
    DayChips(state, onEvent)
    CookSection(state, onEvent)
    DishFields(state, onEvent)
    if (!state.showRejectDialog) ErrorText(state.error)
    SubmitButton(
        text = saveLabelOf(state),
        enabled = state.canSave,
        loading = state.isBusy,
        onClick = { onEvent(DishEditEvent.Save) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.canDelete) {
        OutlinedButton(
            onClick = { onEvent(DishEditEvent.RequestDelete) },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.dish_delete)) }
    }
}

@Composable
private fun EditorDialogs(
    state: DishEditUiState,
    onEvent: (DishEditEvent) -> Unit,
) {
    if (state.showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.dish_delete_title),
            text = stringResource(R.string.dish_delete_text),
            confirmLabel = stringResource(R.string.dish_delete_confirm),
            onConfirm = { onEvent(DishEditEvent.ConfirmDelete) },
            onDismiss = { onEvent(DishEditEvent.CancelDelete) },
        )
    }
    if (state.showRejectDialog) {
        val cookName =
            state.children
                .firstOrNull { it.memberId == state.cookId }
                ?.name
                .orEmpty()
        ReasonDialog(
            title = stringResource(R.string.review_reject_title),
            text = stringResource(R.string.review_reject_text, cookName),
            reason = state.rejectReason,
            onReasonChange = { onEvent(DishEditEvent.RejectReasonChanged(it)) },
            confirmLabel = stringResource(R.string.review_reject_confirm),
            error = state.error,
            isBusy = state.isBusy,
            onConfirm = { onEvent(DishEditEvent.ConfirmReject) },
            onDismiss = { onEvent(DishEditEvent.CancelReject) },
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
                val label = "${weekdayShort(day.dayOfWeek)} ${formatDate(day)}"
                val description =
                    when {
                        day in state.takenDates -> stringResource(R.string.dish_day_taken, label)
                        state.childCook != null && day !in state.allowedDates -> stringResource(R.string.dish_day_absent, label)
                        else -> label
                    }
                FilterChip(
                    selected = day == state.date,
                    onClick = { onEvent(DishEditEvent.DateSelected(day)) },
                    // Der aktuelle Tag bleibt sichtbar ausgewählt, auch wenn er (nicht mehr) wählbar ist.
                    enabled = day in state.allowedDates || day == state.date,
                    label = { Text(weekdayShort(day.dayOfWeek)) },
                    modifier = Modifier.semantics { contentDescription = description },
                )
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
        enabled = !state.fieldsLocked,
        supportingText = { Text("${state.name.length}/${PlanLimits.NAME_MAX}") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.ingredientsText,
        onValueChange = { onEvent(DishEditEvent.IngredientsChanged(it)) },
        label = {
            Text(stringResource(if (state.ingredientsRequired) R.string.dish_ingredients_required else R.string.dish_ingredients))
        },
        singleLine = false,
        minLines = INGREDIENT_MIN_LINES,
        enabled = !state.fieldsLocked,
        modifier = Modifier.fillMaxWidth(),
    )
}
