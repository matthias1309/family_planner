package de.familyplanner.android.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.R
import de.familyplanner.android.core.ui.message

private val SpinnerSize = 18.dp

@Composable
fun SubmitButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(onClick = onClick, enabled = enabled && !loading, modifier = modifier) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(SpinnerSize), strokeWidth = 2.dp) else Text(text)
    }
}

@Composable
fun ErrorText(
    error: AppError?,
    modifier: Modifier = Modifier,
) {
    if (error == null) return
    Text(text = error.message(), color = MaterialTheme.colorScheme.error, modifier = modifier)
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
