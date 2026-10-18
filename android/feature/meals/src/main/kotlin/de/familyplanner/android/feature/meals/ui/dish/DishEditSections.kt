package de.familyplanner.android.feature.meals.ui.dish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.plan.EditorMode
import de.familyplanner.android.feature.meals.ui.formatDeadline
import de.familyplanner.android.feature.meals.ui.weekdayShort

@Composable
internal fun ReviewSection(
    state: DishEditUiState,
    onEvent: (DishEditEvent) -> Unit,
) {
    val dish = state.existing ?: return
    val cookName =
        state.children
            .firstOrNull { it.memberId == dish.cookId }
            ?.name
            .orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val late = if (dish.late) " · ${stringResource(R.string.review_late)}" else ""
        Text(stringResource(R.string.review_submitted_by, cookName) + late, style = MaterialTheme.typography.titleMedium)
        Button(onClick = { onEvent(DishEditEvent.Approve) }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.review_approve))
        }
        OutlinedButton(onClick = { onEvent(DishEditEvent.RequestReject) }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.review_reject))
        }
        HorizontalDivider()
        Text(stringResource(R.string.review_save_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun MoveBanner(state: DishEditUiState) {
    val dish = state.existing ?: return
    val cookName =
        state.children
            .firstOrNull { it.memberId == dish.cookId }
            ?.name
            .orEmpty()
    val day = "${weekdayShort(dish.date.dayOfWeek)} ${formatDate(dish.date)}"
    Text(stringResource(R.string.move_banner, cookName, day), color = MaterialTheme.colorScheme.error)
}

/** Hinweise für das Kind: Frist, Ablehnungsgrund, erneute Freigabe, ungültiger Tag. */
@Composable
internal fun ChildHints(state: DishEditUiState) {
    val dish = state.existing
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val deadline = state.childCook?.submissionDeadline
        Text(
            if (deadline != null) {
                stringResource(R.string.child_cook_line, formatDeadline(deadline))
            } else {
                stringResource(R.string.child_cook_line_no_deadline)
            },
            style = MaterialTheme.typography.titleSmall,
        )
        if (state.isLate) Text(stringResource(R.string.child_late_hint), color = MaterialTheme.colorScheme.tertiary)
        if (dish?.status == DishStatus.REJECTED) {
            Text(stringResource(R.string.child_rejected, dish.rejectionReason.orEmpty()), color = MaterialTheme.colorScheme.error)
        }
        if (dish?.status == DishStatus.APPROVED) Text(stringResource(R.string.child_reapproval_hint))
        if (dish != null && !dish.dayValid) Text(stringResource(R.string.child_day_invalid_hint), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun CookSection(
    state: DishEditUiState,
    onEvent: (DishEditEvent) -> Unit,
) {
    when (state.mode) {
        EditorMode.CHILD -> Unit
        EditorMode.PARENT_MOVE -> {
            val name =
                state.children
                    .firstOrNull { it.memberId == state.cookId }
                    ?.name
                    .orEmpty()
            Column {
                Text(stringResource(R.string.dish_cook), style = MaterialTheme.typography.titleSmall)
                Text(name)
            }
        }
        else -> CookPicker(state, onEvent)
    }
}

@Composable
private fun CookPicker(
    state: DishEditUiState,
    onEvent: (DishEditEvent) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val childLabels = state.children.associate { it.memberId to stringResource(R.string.dish_cook_child, it.name) }
    val selected =
        state.parents.firstOrNull { it.id == state.cookId }?.name
            ?: childLabels[state.cookId]
            ?: stringResource(R.string.dish_cook_pick)
    Column {
        Text(stringResource(R.string.dish_cook), style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.parents.forEach { parent ->
                    CookItem(parent.name, parent.id, onEvent) { expanded = false }
                }
                state.children.forEach { child ->
                    CookItem(childLabels.getValue(child.memberId), child.memberId, onEvent) { expanded = false }
                }
            }
        }
    }
}

@Composable
private fun CookItem(
    label: String,
    memberId: String,
    onEvent: (DishEditEvent) -> Unit,
    close: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = {
            close()
            onEvent(DishEditEvent.CookSelected(memberId))
        },
    )
}
