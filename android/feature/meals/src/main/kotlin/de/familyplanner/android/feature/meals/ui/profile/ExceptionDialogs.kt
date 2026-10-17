package de.familyplanner.android.feature.meals.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatDateRange
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceLimits
import de.familyplanner.android.feature.meals.domain.attendance.Presence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import de.familyplanner.android.core.ui.R as UiR

/** Der Material3-Picker liefert Datumswerte als UTC-Mitternacht — mit der Systemzone gäbe es einen Tag Versatz. */
internal fun pickerMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

internal fun dateToPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
private object SupportedYears : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        pickerMillisToDate(utcTimeMillis).year in AttendanceLimits.MIN_YEAR..AttendanceLimits.MAX_YEAR

    override fun isSelectableYear(year: Int): Boolean = year in AttendanceLimits.MIN_YEAR..AttendanceLimits.MAX_YEAR
}

@Composable
fun AddExceptionDialog(
    error: AppError?,
    isBusy: Boolean,
    onSubmit: (from: LocalDate, to: LocalDate, presence: Presence, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var from by rememberSaveable { mutableStateOf<String?>(null) }
    var to by rememberSaveable { mutableStateOf<String?>(null) }
    var present by rememberSaveable { mutableStateOf(true) }
    var note by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    val fromDate = from?.let(LocalDate::parse)
    val toDate = to?.let(LocalDate::parse)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exceptions_add_title)) },
        text = {
            Column {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (fromDate != null && toDate != null) {
                            formatDateRange(fromDate, toDate)
                        } else {
                            stringResource(R.string.exceptions_pick_range)
                        },
                    )
                }
                PresenceChoice(present) { present = it }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(AttendanceLimits.NOTE_MAX) },
                    label = { Text(stringResource(R.string.exceptions_note)) },
                    keyboardOptions = KeyboardOptions.Default,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                ErrorText(error, Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (fromDate != null && toDate != null) onSubmit(fromDate, toDate, presenceOf(present), note) },
                enabled = fromDate != null && toDate != null && !isBusy,
            ) { Text(stringResource(R.string.exceptions_add_submit)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    )
    if (picking) {
        RangePickerDialog(
            onPicked = { start, end ->
                from = start.toString()
                to = end.toString()
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private fun presenceOf(present: Boolean) = if (present) Presence.PRESENT else Presence.ABSENT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresenceChoice(
    present: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val options = listOf(true to R.string.exceptions_present, false to R.string.exceptions_absent)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = present == value,
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
private fun RangePickerDialog(
    onPicked: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDateRangePickerState(selectableDates = SupportedYears)
    val start = pickerState.selectedStartDateMillis?.let(::pickerMillisToDate)
    val end = pickerState.selectedEndDateMillis?.let(::pickerMillisToDate)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            // Ein einzelner Tag ist ein gültiger Zeitraum: Ende fehlt → gleicher Tag.
            TextButton(onClick = { if (start != null) onPicked(start, end ?: start) }, enabled = start != null) {
                Text(stringResource(UiR.string.action_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    ) {
        DateRangePicker(state = pickerState, modifier = Modifier.weight(1f, fill = false))
    }
}
