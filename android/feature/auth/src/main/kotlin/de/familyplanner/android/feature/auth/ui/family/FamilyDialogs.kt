package de.familyplanner.android.feature.auth.ui.family

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatTime
import de.familyplanner.android.feature.auth.R
import de.familyplanner.android.feature.auth.domain.AuthLimits
import de.familyplanner.android.core.ui.R as UiR

@Composable
fun AddMemberDialog(
    error: AppError?,
    isBusy: Boolean,
    onSubmit: (name: String, role: Role) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(Role.CHILD) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_member_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(AuthLimits.NAME_MAX) },
                    label = { Text(stringResource(R.string.add_member_name)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Role.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = role == entry,
                            onClick = { role = entry },
                            shape = SegmentedButtonDefaults.itemShape(index, Role.entries.size),
                        ) {
                            Text(stringResource(entry.labelRes()))
                        }
                    }
                }
                ErrorText(error, Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name, role) }, enabled = name.isNotBlank() && !isBusy) {
                Text(stringResource(R.string.add_member_submit))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    )
}

@Composable
fun PairingCodeDialog(
    dialog: IssuedCodeDialog,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.code_dialog_title, dialog.memberName)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.code_dialog_expiry, dialog.code.code, formatTime(dialog.code.expiresAt)),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(R.string.code_dialog_hint), modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

internal fun Role.labelRes(): Int = if (this == Role.PARENT) R.string.family_role_parent_suffix else R.string.family_role_child_suffix
