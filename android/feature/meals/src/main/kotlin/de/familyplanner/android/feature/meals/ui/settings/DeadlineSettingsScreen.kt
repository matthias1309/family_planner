package de.familyplanner.android.feature.meals.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.components.SubmitButton
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.attendance.DeadlineSettings
import de.familyplanner.android.feature.meals.ui.WeekDays
import de.familyplanner.android.feature.meals.ui.weekdayLong
import org.koin.androidx.compose.koinViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import de.familyplanner.android.core.ui.R as UiR

@Composable
fun DeadlineSettingsRoute(onBack: () -> Unit) {
    val viewModel = koinViewModel<DeadlineSettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeadlineSettingsScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineSettingsScreen(
    state: DeadlineSettingsUiState,
    onEvent: (DeadlineSettingsEvent) -> Unit,
    onBack: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(DeadlineSettingsEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium) }
            item { ErrorText(state.error) }
            state.overview?.let { overview ->
                item { Text(stringResource(R.string.settings_current, describe(overview.current))) }
                overview.scheduled?.let { scheduled ->
                    item {
                        Text(stringResource(R.string.settings_scheduled, formatDate(scheduled.effectiveFrom), describe(scheduled)))
                    }
                }
                item { SettingsForm(state, onEvent) }
            }
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
        }
    }
}

@Composable
private fun describe(settings: DeadlineSettings): String =
    stringResource(
        R.string.settings_description,
        weekdayLong(settings.orderDay),
        settings.orderTime.toString(),
        settings.childTime.toString(),
    )

@Composable
private fun SettingsForm(
    state: DeadlineSettingsUiState,
    onEvent: (DeadlineSettingsEvent) -> Unit,
) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_order_deadline), style = MaterialTheme.typography.titleSmall)
        DayDropdown(state.orderDay) { onEvent(DeadlineSettingsEvent.OrderDayChanged(it)) }
        TimeField(stringResource(R.string.settings_order_time), state.orderTime) { onEvent(DeadlineSettingsEvent.OrderTimeChanged(it)) }
        TimeField(stringResource(R.string.settings_child_time), state.childTime) { onEvent(DeadlineSettingsEvent.ChildTimeChanged(it)) }
        Text(stringResource(R.string.settings_hint), style = MaterialTheme.typography.bodySmall)
        ErrorText(state.saveError)
        SubmitButton(
            text = stringResource(R.string.edit_save),
            enabled = true,
            loading = state.isBusy,
            onClick = { onEvent(DeadlineSettingsEvent.Save) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DayDropdown(
    selected: DayOfWeek,
    onSelect: (DayOfWeek) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(weekdayLong(selected)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WeekDays.forEach { day ->
                DropdownMenuItem(
                    text = { Text(weekdayLong(day)) },
                    onClick = {
                        expanded = false
                        onSelect(day)
                    },
                )
            }
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    time: LocalTime,
    onChange: (LocalTime) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $time") }
    if (picking) {
        TimePickerDialog(
            initial = time,
            onConfirm = {
                picking = false
                onChange(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(UiR.string.action_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    )
}
