package de.familyplanner.android.feature.meals.ui.plan

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.ui.formatDeadline
import de.familyplanner.android.feature.meals.ui.weekdayShort
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

private const val INGREDIENT_PREVIEW_LINES = 2

@Composable
fun WeekPlanRoute(
    onOpenDay: (date: LocalDate, dishId: String?) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<WeekPlanViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Nach dem Zurückkehren aus dem Editor hat sich der Plan evtl. geändert.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(WeekPlanEvent.Refresh)
        onPauseOrDispose {}
    }
    WeekPlanScreen(state = state, onEvent = viewModel::onEvent, onOpenDay = onOpenDay, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPlanScreen(
    state: WeekPlanUiState,
    onEvent: (WeekPlanEvent) -> Unit,
    onOpenDay: (date: LocalDate, dishId: String?) -> Unit,
    onBack: () -> Unit,
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
            state.week?.takeIf { state.isOffline }?.let { week ->
                item { OfflineNotice(stringResource(R.string.plan_offline, formatDeadline(week.syncedAt)), state.isParent) }
            }
            item { ErrorText(state.error) }
            state.week?.let { week ->
                items(week.days, key = { it.date.toString() }) { day ->
                    PlanDayRow(day, isToday = day.date == state.today, canEdit = state.canEdit, onOpenDay = onOpenDay)
                }
            }
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
        }
    }
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
    canEdit: Boolean,
    onOpenDay: (LocalDate, String?) -> Unit,
) {
    val editLabel = stringResource(if (day.dish == null) R.string.plan_day_add_action else R.string.plan_day_edit_action)
    val rowModifier =
        if (canEdit) {
            Modifier.fillMaxWidth().clickable(onClickLabel = editLabel) { onOpenDay(day.date, day.dish?.id) }
        } else {
            Modifier.fillMaxWidth()
        }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = rowModifier.padding(vertical = 4.dp)) {
        DayLabel(day, isToday)
        DishSummary(day, canEdit, modifier = Modifier.weight(1f))
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
        if (dish.ingredients.isNotEmpty()) {
            Text(
                dish.ingredients.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = INGREDIENT_PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
