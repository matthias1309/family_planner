package de.familyplanner.android.feature.tasks.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.familyplanner.android.feature.tasks.R
import de.familyplanner.android.feature.tasks.domain.board.TaskLimits

private const val REASON_MIN_LINES = 2

/** Zurückweisen mit optionaler Begründung (REQ-TASK-023): Bestätigen ist auch ohne Text möglich. */
@Composable
fun RejectDialog(
    title: String,
    reason: String,
    onReasonChange: (String) -> Unit,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_reject_title)) },
        text = {
            Column {
                Text(stringResource(R.string.tasks_reject_text, title))
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text(stringResource(R.string.tasks_reject_reason)) },
                    minLines = REASON_MIN_LINES,
                    supportingText = { Text("${reason.length}/${TaskLimits.REASON_MAX}") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !isBusy) { Text(stringResource(R.string.tasks_action_reject)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tasks_cancel)) } },
    )
}
