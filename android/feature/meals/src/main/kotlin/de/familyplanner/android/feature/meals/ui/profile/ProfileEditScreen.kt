package de.familyplanner.android.feature.meals.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.components.SubmitButton
import de.familyplanner.android.core.ui.formatDateRange
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.attendance.WeekType
import de.familyplanner.android.feature.meals.ui.WeekDays
import de.familyplanner.android.feature.meals.ui.weekdayShort
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ProfileEditRoute(
    memberId: String,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<ProfileEditViewModel> { parametersOf(memberId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileEditScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    state: ProfileEditUiState,
    onEvent: (ProfileEditEvent) -> Unit,
    onBack: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(ProfileEditEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(state.name.ifEmpty { stringResource(R.string.edit_title) }, style = MaterialTheme.typography.headlineMedium) }
            item { ErrorText(state.error) }
            if (state.isLoaded) {
                item { ProfileForm(state, onEvent) }
                exceptionsSection(state, onEvent)
            }
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
        }
    }
    ExceptionDialogsHost(state, onEvent)
}

@Composable
private fun ProfileForm(
    state: ProfileEditUiState,
    onEvent: (ProfileEditEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.edit_cooks), modifier = Modifier.weight(1f))
            Switch(checked = state.cooks, onCheckedChange = { onEvent(ProfileEditEvent.CooksChanged(it)) })
        }
        PatternChoice(state.alternating) { onEvent(ProfileEditEvent.AlternatingChanged(it)) }
        if (state.alternating) {
            DayChips(stringResource(R.string.edit_week_a), WeekType.A, state.weekA, onEvent)
            DayChips(stringResource(R.string.edit_week_b), WeekType.B, state.weekB, onEvent)
            ThisWeekChoice(state, onEvent)
        }
        ErrorText(state.saveError)
        if (state.saved) Text(stringResource(R.string.edit_saved), color = MaterialTheme.colorScheme.primary)
        SubmitButton(
            text = stringResource(R.string.edit_save),
            enabled = true,
            loading = state.isBusy,
            onClick = { onEvent(ProfileEditEvent.Save) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatternChoice(
    alternating: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val options = listOf(false to R.string.edit_pattern_always, true to R.string.edit_pattern_alternating)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = alternating == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayChips(
    label: String,
    week: WeekType,
    selected: Set<java.time.DayOfWeek>,
    onEvent: (ProfileEditEvent) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeekDays.forEach { day ->
                FilterChip(
                    selected = day in selected,
                    onClick = { onEvent(ProfileEditEvent.DayToggled(week, day)) },
                    label = { Text(weekdayShort(day)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThisWeekChoice(
    state: ProfileEditUiState,
    onEvent: (ProfileEditEvent) -> Unit,
) {
    val range = formatDateRange(state.weekOfToday, state.weekOfToday.plusDays(6))
    Column {
        Text(stringResource(R.string.edit_this_week, range), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            WeekType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = state.thisWeekType == type,
                    onClick = { onEvent(ProfileEditEvent.ThisWeekChanged(type)) },
                    shape = SegmentedButtonDefaults.itemShape(index, WeekType.entries.size),
                ) {
                    Text(type.name)
                }
            }
        }
    }
}
