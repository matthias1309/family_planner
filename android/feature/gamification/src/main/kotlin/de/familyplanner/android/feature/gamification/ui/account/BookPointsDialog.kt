package de.familyplanner.android.feature.gamification.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.PointsLimits

@Composable
fun BookPointsDialog(
    draft: BookingDraft,
    isBusy: Boolean,
    error: AppError?,
    onEvent: (ChildAccountEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ChildAccountEvent.DismissBooking) },
        title = { Text(stringResource(R.string.game_book_points)) },
        text = {
            Column {
                Row {
                    FilterChip(
                        selected = draft.credit,
                        onClick = { onEvent(ChildAccountEvent.BookingCreditChanged(true)) },
                        label = { Text(stringResource(R.string.game_book_credit)) },
                    )
                    FilterChip(
                        selected = !draft.credit,
                        onClick = { onEvent(ChildAccountEvent.BookingCreditChanged(false)) },
                        label = { Text(stringResource(R.string.game_book_debit)) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = draft.amountText,
                    onValueChange = { onEvent(ChildAccountEvent.BookingAmountChanged(it)) },
                    label = { Text(stringResource(R.string.game_book_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = draft.amountTooHigh,
                    supportingText = {
                        if (draft.amountTooHigh) {
                            Text(
                                stringResource(R.string.game_book_amount_max, PointsLimits.AMOUNT_MAX),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.reason,
                    onValueChange = { onEvent(ChildAccountEvent.BookingReasonChanged(it)) },
                    label = { Text(stringResource(R.string.game_book_reason)) },
                    placeholder = { Text(stringResource(R.string.game_book_reason_placeholder)) },
                    supportingText = { Text("${draft.reason.length}/${PointsLimits.REASON_MAX}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ChildAccountEvent.SubmitBooking) }, enabled = draft.canSubmit && !isBusy) {
                Text(stringResource(R.string.game_book_submit))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(ChildAccountEvent.DismissBooking) },
            ) { Text(stringResource(R.string.game_cancel)) }
        },
    )
}
