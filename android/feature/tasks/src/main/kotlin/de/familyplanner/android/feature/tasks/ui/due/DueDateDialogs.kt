package de.familyplanner.android.feature.tasks.ui.due

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.core.ui.formatWeekdayDateTime
import de.familyplanner.android.feature.tasks.R
import de.familyplanner.android.feature.tasks.domain.board.TaskLimits
import de.familyplanner.android.feature.tasks.domain.board.dueInstant
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import de.familyplanner.android.core.ui.R as UiR

/** Der Picker liefert UTC-Mitternacht des gewählten Tages. */
internal fun pickerMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

internal fun dateToPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Vorbelegte Uhrzeit: die der bisherigen Frist, sonst 18:00. */
internal fun initialDueTime(initial: Instant?): LocalTime =
    initial?.atZone(BerlinZone)?.toLocalTime() ?: LocalTime.of(TaskLimits.DUE_DEFAULT_HOUR, 0)

@OptIn(ExperimentalMaterial3Api::class)
private class FromToday(
    private val today: LocalDate,
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = !pickerMillisToDate(utcTimeMillis).isBefore(today)

    override fun isSelectableYear(year: Int): Boolean = year >= today.year
}

/** Frist wählen: erst das Datum, dann die Uhrzeit. [allowRemove] bietet „Frist entfernen“ im Datumsschritt (REQ-TASK-026). */
@Composable
fun DueDatePickerDialog(
    initial: Instant?,
    now: Instant,
    allowRemove: Boolean,
    onConfirm: (Instant?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Als Epoch-Tag gespeichert, damit der Schritt eine Drehung übersteht.
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val day = pickedDay
    if (day == null) {
        DateStep(
            initial,
            now,
            allowRemove,
            onPicked = { pickedDay = it.toEpochDay() },
            onRemove = { onConfirm(null) },
            onDismiss = onDismiss,
        )
    } else {
        TimeStep(initial, onConfirm = { onConfirm(dueInstant(LocalDate.ofEpochDay(day), it)) }, onDismiss = onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateStep(
    initial: Instant?,
    now: Instant,
    allowRemove: Boolean,
    onPicked: (LocalDate) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val today = now.atZone(BerlinZone).toLocalDate()
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initial?.atZone(BerlinZone)?.toLocalDate()?.let(::dateToPickerMillis),
            selectableDates = remember(today) { FromToday(today) },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val date = state.selectedDateMillis?.let(::pickerMillisToDate)
            TextButton(onClick = { if (date != null) onPicked(date) }, enabled = date != null) {
                Text(stringResource(R.string.tasks_due_next))
            }
        },
        dismissButton = {
            if (allowRemove) TextButton(onClick = onRemove) { Text(stringResource(R.string.tasks_due_remove)) }
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeStep(
    initial: Instant?,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = initialDueTime(initial)
    val state = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_due_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(UiR.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    )
}

/** Knopf „Frist: keine“ / „Frist: Sa. 01.11. 18:00“, der den Frist-Dialog öffnet (beim Zuweisen). */
@Composable
fun DueDateField(
    dueAt: Instant?,
    now: Instant,
    onChange: (Instant?) -> Unit,
    enabled: Boolean,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val label =
        if (dueAt ==
            null
        ) {
            stringResource(R.string.tasks_due_none)
        } else {
            stringResource(R.string.tasks_due_value, formatWeekdayDateTime(dueAt))
        }
    OutlinedButton(onClick = { picking = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
    if (picking) {
        DueDatePickerDialog(
            initial = dueAt,
            now = now,
            allowRemove = dueAt != null,
            onConfirm = {
                picking = false
                onChange(it)
            },
            onDismiss = { picking = false },
        )
    }
}
