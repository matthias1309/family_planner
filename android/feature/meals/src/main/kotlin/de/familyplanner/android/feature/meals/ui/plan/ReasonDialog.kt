package de.familyplanner.android.feature.meals.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits

private const val REASON_MIN_LINES = 2

/** Dialog mit Pflichtbegründung (Absage und Ablehnung). */
@Composable
fun ReasonDialog(
    title: String,
    text: String,
    reason: String,
    onReasonChange: (String) -> Unit,
    confirmLabel: String,
    error: AppError?,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { onReasonChange(it.take(PlanLimits.REASON_MAX)) },
                    label = { Text(stringResource(R.string.dish_reason_label)) },
                    minLines = REASON_MIN_LINES,
                    supportingText = { Text("${reason.length}/${PlanLimits.REASON_MAX}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorText(error)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !isBusy) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dish_cancel)) } },
    )
}
