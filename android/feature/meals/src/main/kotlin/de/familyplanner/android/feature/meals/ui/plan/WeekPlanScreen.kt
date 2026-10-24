package de.familyplanner.android.feature.meals.ui.plan

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.ui.dish.DishEditArgs
import de.familyplanner.android.feature.meals.ui.formatDeadline
import de.familyplanner.android.feature.meals.ui.recipe.RecipeViewArgs
import de.familyplanner.android.feature.meals.ui.weekdayShort
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

private const val INGREDIENT_PREVIEW_LINES = 2
private const val WHATSAPP_PACKAGE = "com.whatsapp"

/** Textvorlage für die Eltern; ohne WhatsApp öffnet sich die allgemeine Teilen-Auswahl. */
private fun shareViaWhatsApp(context: Context) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.whatsapp_template))
    try {
        context.startActivity(Intent(send).setPackage(WHATSAPP_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (
        @Suppress("SwallowedException") e: ActivityNotFoundException,
    ) {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun WeekPlanRoute(
    onOpenEditor: (DishEditArgs) -> Unit,
    onOpenRecipe: (RecipeViewArgs) -> Unit,
    onBack: () -> Unit,
    onOpenShopping: (LocalDate) -> Unit = {},
) {
    val viewModel = koinViewModel<WeekPlanViewModel>()
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Nach dem Zurückkehren aus dem Editor hat sich der Plan evtl. geändert.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(WeekPlanEvent.Refresh)
        onPauseOrDispose {}
    }
    WeekPlanScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenEditor = onOpenEditor,
        onOpenRecipe = onOpenRecipe,
        onBack = onBack,
        onOpenShopping = onOpenShopping,
        onShareWhatsApp = { shareViaWhatsApp(context) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPlanScreen(
    state: WeekPlanUiState,
    onEvent: (WeekPlanEvent) -> Unit,
    onOpenEditor: (DishEditArgs) -> Unit,
    onOpenRecipe: (RecipeViewArgs) -> Unit,
    onBack: () -> Unit,
    onOpenShopping: (LocalDate) -> Unit = {},
    onShareWhatsApp: () -> Unit = {},
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(WeekPlanEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { WeekHeader(state, onEvent) }
            if (state.showsPlanWeekLink) item { PlanWeekLink(state, onEvent) }
            state.week?.takeIf { state.isOffline }?.let { week ->
                val notice = if (state.cacheReason == AppError.Server) R.string.plan_server_problem else R.string.plan_offline
                item { OfflineNotice(stringResource(notice, formatDeadline(week.syncedAt)), state.isParent) }
            }
            item { ErrorText(state.error) }
            state.pendingCooking?.takeIf { state.showsWhatsAppHint }?.let { pending ->
                item { WhatsAppHintCard(pending, onShareWhatsApp) }
            }
            if (state.isOffline && state.weekStart >= state.planWeek) item { Text(stringResource(R.string.status_offline)) }
            if (state.showsStatus && (state.status != null || state.statusError != null)) {
                item { StatusCards(state, onEvent, onOpenEditor, onOpenShopping) }
            }
            if (state.actionError != null && state.cancelTarget == null) item { ErrorText(state.actionError) }
            state.week?.let { week ->
                items(week.days, key = { it.date.toString() }) { day ->
                    PlanDayRow(
                        day,
                        isToday = day.date == state.today,
                        state = state,
                        onOpenEditor = onOpenEditor,
                        onOpenRecipe = onOpenRecipe,
                    )
                }
            }
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
        }
    }
    CancelDialog(state, onEvent)
}

@Composable
private fun StatusCards(
    state: WeekPlanUiState,
    onEvent: (WeekPlanEvent) -> Unit,
    onOpenEditor: (DishEditArgs) -> Unit,
    onOpenShopping: (LocalDate) -> Unit,
) {
    val actions =
        StatusActions(
            onOpen = onOpenEditor,
            onCancel = { onEvent(WeekPlanEvent.RequestCancel(it)) },
            onWithdraw = { onEvent(WeekPlanEvent.Withdraw(it)) },
            onOpenShopping = onOpenShopping,
        )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.myCooking?.let { cook ->
            MyCookingCard(
                cook = cook,
                weekStart = state.weekStart,
                rejectionReason = state.rejectionReasonOf(cook.status.dishId),
                dishDate = cook.status.dishId?.let(state::dishDate),
                actions = actions,
            )
        }
        WeekStatusCard(state.status, state.statusError, state.isParent, state::dishDate, actions)
    }
}

@Composable
private fun PlanWeekLink(
    state: WeekPlanUiState,
    onEvent: (WeekPlanEvent) -> Unit,
) {
    TextButton(onClick = { onEvent(WeekPlanEvent.GoToPlanWeek) }) {
        Text(stringResource(R.string.plan_week_link, formatDate(state.planWeek), formatDate(state.planWeek.plusDays(6))))
    }
}

@Composable
private fun CancelDialog(
    state: WeekPlanUiState,
    onEvent: (WeekPlanEvent) -> Unit,
) {
    val target = state.cancelTarget ?: return
    val text =
        if (target == state.memberId) {
            stringResource(R.string.cooking_cancel_text_self)
        } else {
            val name =
                state.status
                    ?.cooks
                    ?.firstOrNull { it.status.memberId == target }
                    ?.name
                    .orEmpty()
            stringResource(R.string.cooking_cancel_text_other, name)
        }
    ReasonDialog(
        title = stringResource(R.string.cooking_cancel_title),
        text = text,
        reason = state.cancelReason,
        onReasonChange = { onEvent(WeekPlanEvent.CancelReasonChanged(it)) },
        confirmLabel = stringResource(R.string.cooking_cancel_confirm),
        error = state.actionError,
        isBusy = state.isBusy,
        onConfirm = { onEvent(WeekPlanEvent.ConfirmCancel) },
        onDismiss = { onEvent(WeekPlanEvent.DismissCancel) },
    )
}

@Composable
private fun WeekHeader(
    state: WeekPlanUiState,
    onEvent: (WeekPlanEvent) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { onEvent(WeekPlanEvent.PreviousWeek) }, enabled = state.canGoPrevious) {
            val previous = stringResource(R.string.week_previous)
            Text("‹", modifier = Modifier.semantics { contentDescription = previous })
        }
        Text(
            stringResource(R.string.week_title, formatDate(state.weekStart), formatDate(state.weekStart.plusDays(6))),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onEvent(WeekPlanEvent.NextWeek) }, enabled = state.canGoNext) {
            val next = stringResource(R.string.week_next)
            Text("›", modifier = Modifier.semantics { contentDescription = next })
        }
    }
}

@Composable
private fun OfflineNotice(
    text: String,
    isParent: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
        if (isParent) {
            Text(stringResource(R.string.plan_edit_hint_offline), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlanDayRow(
    day: PlannedDay,
    isToday: Boolean,
    state: WeekPlanUiState,
    onOpenEditor: (DishEditArgs) -> Unit,
    onOpenRecipe: (RecipeViewArgs) -> Unit,
) {
    val editLabel = stringResource(if (day.dish == null) R.string.plan_day_add_action else R.string.plan_day_edit_action)
    val rowModifier =
        if (state.canOpen(day)) {
            Modifier.fillMaxWidth().clickable(
                onClickLabel = editLabel,
                role = Role.Button,
            ) { onOpenEditor(DishEditArgs(day.date, day.dish?.id)) }
        } else {
            Modifier.fillMaxWidth()
        }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = rowModifier.padding(vertical = 4.dp)) {
        DayLabel(day, isToday)
        DishSummary(day, state.canEdit, showRecipeLink = !state.isOffline, onOpenRecipe = onOpenRecipe, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DayLabel(
    day: PlannedDay,
    isToday: Boolean,
) {
    Column {
        Text("${weekdayShort(day.date.dayOfWeek)} ${formatDate(day.date)}", style = MaterialTheme.typography.titleSmall)
        if (isToday) {
            Text(
                stringResource(R.string.plan_today),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun DishSummary(
    day: PlannedDay,
    canEdit: Boolean,
    showRecipeLink: Boolean,
    onOpenRecipe: (RecipeViewArgs) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dish = day.dish
    Column(modifier = modifier) {
        if (dish == null) {
            Text(stringResource(R.string.plan_open), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canEdit) Text(stringResource(R.string.plan_add), color = MaterialTheme.colorScheme.primary)
            return@Column
        }
        Text(dish.name, style = MaterialTheme.typography.titleMedium)
        day.cookName?.let { Text(stringResource(R.string.plan_cook, it), style = MaterialTheme.typography.bodyMedium) }
        DishBadges(dish)
        if (dish.ingredients.isNotEmpty()) {
            Text(
                dish.ingredients.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = INGREDIENT_PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Rezepte sind nicht offline lesbar (PRD „Later“): beim gespeicherten Stand kein Link.
        val recipeId = dish.recipeId
        if (recipeId != null && showRecipeLink) {
            val description = stringResource(R.string.plan_recipe_description, dish.name)
            TextButton(
                onClick = { onOpenRecipe(RecipeViewArgs(recipeId, dish.date, dish.id)) },
                modifier = Modifier.semantics { contentDescription = description },
            ) { Text(stringResource(R.string.plan_recipe)) }
        }
    }
}

@Composable
private fun DishBadges(dish: Dish) {
    val badges =
        listOfNotNull(
            when (dish.status) {
                DishStatus.SUBMITTED -> stringResource(R.string.plan_badge_submitted)
                DishStatus.REJECTED -> stringResource(R.string.plan_badge_rejected)
                DishStatus.APPROVED -> null
            },
            if (dish.late) stringResource(R.string.plan_badge_late) else null,
        )
    if (badges.isNotEmpty()) {
        Text(badges.joinToString(" · "), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
    }
    if (!dish.dayValid) {
        Text(
            stringResource(R.string.plan_badge_invalid),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
