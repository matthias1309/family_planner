package de.familyplanner.android.feature.meals.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.cooking.CookOverview
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.cooking.StatusOverview
import de.familyplanner.android.feature.meals.ui.dish.DishEditArgs
import de.familyplanner.android.feature.meals.ui.formatDeadline
import de.familyplanner.android.feature.meals.ui.weekdayShort
import java.time.LocalDate

private val CardPadding = 16.dp

/** Wochenstatus für alle; Aktionen (Eintragen, Absagen, Zurücknehmen, Gericht öffnen) nur für Eltern. */
@Composable
fun WeekStatusCard(
    status: StatusOverview?,
    error: AppError?,
    isParent: Boolean,
    dishDateOf: (String) -> LocalDate?,
    actions: StatusActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(CardPadding)) {
            Text(stringResource(R.string.status_title), style = MaterialTheme.typography.titleMedium)
            ErrorText(error)
            if (status != null) {
                Text(stringResource(R.string.status_order_deadline, formatDeadline(status.orderDeadline)))
                ReadyOrOpen(status)
                status.cooks.forEach { cook -> CookRow(status, cook, isParent, dishDateOf, actions) }
                TextButton(onClick = { actions.onOpenShopping(status.weekStart) }) {
                    Text(stringResource(R.string.status_shopping))
                }
            }
        }
    }
}

@Composable
private fun ReadyOrOpen(status: StatusOverview) {
    if (status.readyToOrder) {
        Text(stringResource(R.string.status_ready), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    } else if (status.openDays.isNotEmpty()) {
        val days = status.openDays.joinToString(", ") { "${weekdayShort(it.dayOfWeek)} ${formatDate(it)}" }
        Text(stringResource(R.string.status_open_days, days))
    }
}

@Composable
private fun cookStateText(cook: CookOverview): String =
    when (cook.status.state) {
        CookState.NOT_SUBMITTED -> stringResource(R.string.status_not_submitted)
        CookState.SUBMITTED -> stringResource(R.string.status_submitted)
        CookState.APPROVED -> stringResource(R.string.status_approved)
        CookState.REJECTED -> stringResource(R.string.status_rejected)
        CookState.CANCELLED ->
            cook.status.cancellationReason?.let { stringResource(R.string.status_cancelled_reason, it) }
                ?: stringResource(R.string.status_cancelled)
    }

@Composable
private fun CookRow(
    overview: StatusOverview,
    cook: CookOverview,
    isParent: Boolean,
    dishDateOf: (String) -> LocalDate?,
    actions: StatusActions,
) {
    val status = cook.status
    val extras = mutableListOf<String>()
    if (status.state ==
        CookState.NOT_SUBMITTED
    ) {
        status.submissionDeadline?.let { extras += stringResource(R.string.status_until, formatDeadline(it)) }
    }
    if (status.late) extras += stringResource(R.string.status_late)
    if (!status.dayValid) extras += stringResource(R.string.status_day_invalid)
    val line = (listOf(stringResource(R.string.status_cook_line, cook.name, cookStateText(cook))) + extras).joinToString(" · ")
    val dishDate = status.dishId?.let(dishDateOf)
    val openLabel = stringResource(R.string.status_open_dish)
    val rowModifier =
        if (isParent && status.dishId != null && dishDate != null) {
            Modifier.fillMaxWidth().clickable(onClickLabel = openLabel, role = Role.Button) {
                actions.onOpen(DishEditArgs(dishDate, status.dishId))
            }
        } else {
            Modifier.fillMaxWidth()
        }
    Column(modifier = rowModifier) {
        Text(line, color = if (status.dayValid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        if (isParent) ParentCookActions(overview, cook, actions)
    }
}

@Composable
private fun ParentCookActions(
    overview: StatusOverview,
    cook: CookOverview,
    actions: StatusActions,
) {
    val memberId = cook.status.memberId
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        when (cook.status.state) {
            CookState.NOT_SUBMITTED -> {
                TextButton(onClick = { actions.onOpen(DishEditArgs(overview.weekStart, null, memberId)) }) {
                    Text(stringResource(R.string.status_enter))
                }
                TextButton(onClick = { actions.onCancel(memberId) }) { Text(stringResource(R.string.status_cancel)) }
            }
            CookState.CANCELLED ->
                TextButton(onClick = { actions.onWithdraw(memberId) }) { Text(stringResource(R.string.status_withdraw)) }
            else -> Unit
        }
    }
}

/** „Mein Kochtag“ für das kochende Kind (REQ-MEAL-020/022/023). */
@Composable
fun MyCookingCard(
    cook: CookOverview,
    weekStart: LocalDate,
    rejectionReason: String?,
    dishDate: LocalDate?,
    actions: StatusActions,
) {
    val status = cook.status
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(CardPadding)) {
            Text(stringResource(R.string.cooking_title), style = MaterialTheme.typography.titleMedium)
            Text(
                status.submissionDeadline?.let { stringResource(R.string.cooking_deadline, formatDeadline(it)) }
                    ?: stringResource(R.string.cooking_no_deadline),
            )
            CookingStateLine(cook, rejectionReason)
            CookingButtons(cook, weekStart, dishDate, actions)
        }
    }
}

@Composable
private fun CookingStateLine(
    cook: CookOverview,
    rejectionReason: String?,
) {
    val status = cook.status
    val text =
        when (status.state) {
            CookState.NOT_SUBMITTED -> stringResource(R.string.cooking_not_submitted)
            CookState.SUBMITTED -> stringResource(R.string.cooking_submitted)
            CookState.APPROVED -> stringResource(R.string.cooking_approved)
            CookState.REJECTED -> stringResource(R.string.cooking_rejected, rejectionReason.orEmpty())
            CookState.CANCELLED -> stringResource(R.string.cooking_cancelled, status.cancellationReason.orEmpty())
        }
    Text(if (status.late) "$text · ${stringResource(R.string.cooking_late)}" else text, style = MaterialTheme.typography.titleSmall)
    if (!status.dayValid) Text(stringResource(R.string.cooking_day_invalid), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun CookingButtons(
    cook: CookOverview,
    weekStart: LocalDate,
    dishDate: LocalDate?,
    actions: StatusActions,
) {
    val status = cook.status
    val memberId = status.memberId
    if (status.state == CookState.CANCELLED) {
        OutlinedButton(onClick = { actions.onWithdraw(memberId) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cooking_withdraw))
        }
        return
    }
    val dishId = status.dishId
    val label =
        when {
            dishId == null -> R.string.cooking_submit
            status.state == CookState.REJECTED -> R.string.cooking_revise
            else -> R.string.cooking_change
        }
    Button(
        onClick = { actions.onOpen(DishEditArgs(if (dishId != null && dishDate != null) dishDate else weekStart, dishId, memberId)) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(label)) }
    OutlinedButton(onClick = { actions.onCancel(memberId) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.cooking_cancel))
    }
}
