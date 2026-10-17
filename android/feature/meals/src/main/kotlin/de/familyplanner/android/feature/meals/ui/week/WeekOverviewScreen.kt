package de.familyplanner.android.feature.meals.ui.week

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceDay
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceSource
import de.familyplanner.android.feature.meals.domain.attendance.ChildWeek
import de.familyplanner.android.feature.meals.ui.formatDeadline
import de.familyplanner.android.feature.meals.ui.weekdayShort
import org.koin.androidx.compose.koinViewModel

private const val PRESENT_MARK = "●"
private const val ABSENT_MARK = "–"
private const val EXCEPTION_MARK = "◆"

@Composable
fun WeekOverviewRoute(
    onManage: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<WeekOverviewViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    WeekOverviewScreen(state = state, onEvent = viewModel::onEvent, onManage = onManage, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekOverviewScreen(
    state: WeekOverviewUiState,
    onEvent: (WeekOverviewEvent) -> Unit,
    onManage: () -> Unit,
    onBack: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(WeekOverviewEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { WeekHeader(state, onEvent) }
            item { ErrorText(state.error) }
            state.overview?.let { overview ->
                item { Text(stringResource(R.string.week_order_deadline, formatDeadline(overview.orderDeadline))) }
                if (overview.children.isEmpty()) {
                    item { Text(stringResource(R.string.week_no_children)) }
                }
                items(overview.children, key = { it.memberId }) { ChildWeekCard(it) }
            }
            if (state.isParent) {
                item {
                    Button(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.week_manage))
                    }
                }
            }
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
        }
    }
}

@Composable
private fun WeekHeader(
    state: WeekOverviewUiState,
    onEvent: (WeekOverviewEvent) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { onEvent(WeekOverviewEvent.PreviousWeek) }, enabled = state.canGoPrevious) {
            val previous = stringResource(R.string.week_previous)
            Text("‹", modifier = Modifier.semantics { contentDescription = previous })
        }
        Text(
            stringResource(R.string.week_title, formatDate(state.weekStart), formatDate(state.weekStart.plusDays(6))),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onEvent(WeekOverviewEvent.NextWeek) }, enabled = state.canGoNext) {
            val next = stringResource(R.string.week_next)
            Text("›", modifier = Modifier.semantics { contentDescription = next })
        }
    }
}

@Composable
private fun ChildWeekCard(child: ChildWeek) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        val title = if (child.cooks) stringResource(R.string.week_child_cooks, child.name) else child.name
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            child.days.forEach { DayCell(it) }
        }
        if (child.cooks) {
            val deadline = child.submissionDeadline
            Text(
                if (deadline == null) {
                    stringResource(R.string.week_no_cooking_day)
                } else {
                    stringResource(R.string.week_submit_until, formatDeadline(deadline))
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DayCell(day: AttendanceDay) {
    val isException = day.source == AttendanceSource.EXCEPTION
    val mark =
        when {
            isException -> EXCEPTION_MARK
            day.present -> PRESENT_MARK
            else -> ABSENT_MARK
        }
    val presence = stringResource(if (day.present) R.string.week_day_present else R.string.week_day_absent)
    val description = if (isException) stringResource(R.string.week_day_exception, presence) else presence
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${weekdayShort(day.date.dayOfWeek)}: $description" },
    ) {
        Text(weekdayShort(day.date.dayOfWeek), style = MaterialTheme.typography.labelSmall)
        Text(
            mark,
            color = if (isException) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isException) FontWeight.Bold else FontWeight.Normal,
        )
        if (isException) Text(if (day.present) "+" else "×", style = MaterialTheme.typography.labelSmall)
    }
}
