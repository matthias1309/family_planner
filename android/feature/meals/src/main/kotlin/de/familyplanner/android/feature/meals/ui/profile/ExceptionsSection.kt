package de.familyplanner.android.feature.meals.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.familyplanner.android.core.ui.components.ConfirmDialog
import de.familyplanner.android.core.ui.formatDateRange
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceException
import de.familyplanner.android.feature.meals.domain.attendance.Presence

internal fun LazyListScope.exceptionsSection(
    state: ProfileEditUiState,
    onEvent: (ProfileEditEvent) -> Unit,
) {
    item { Text(stringResource(R.string.exceptions_title), style = MaterialTheme.typography.titleMedium) }
    if (state.exceptions.isEmpty()) {
        item { Text(stringResource(R.string.exceptions_empty)) }
    }
    items(state.exceptions.size, key = { state.exceptions[it].id }) { index ->
        val exception = state.exceptions[index]
        ExceptionRow(exception) { onEvent(ProfileEditEvent.RequestDelete(exception)) }
    }
    item {
        OutlinedButton(onClick = { onEvent(ProfileEditEvent.ShowAddException) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.exceptions_add))
        }
    }
}

@Composable
private fun ExceptionRow(
    exception: AttendanceException,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(formatDateRange(exception.from, exception.to), style = MaterialTheme.typography.bodyLarge)
            val presence =
                stringResource(if (exception.presence == Presence.PRESENT) R.string.exceptions_present else R.string.exceptions_absent)
            Text(listOfNotNull(presence, exception.note).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.exceptions_delete))
        }
    }
}

@Composable
internal fun ExceptionDialogsHost(
    state: ProfileEditUiState,
    onEvent: (ProfileEditEvent) -> Unit,
) {
    if (state.showAddException) {
        AddExceptionDialog(
            error = state.addExceptionError,
            isBusy = state.isBusy,
            onSubmit = { from, to, presence, note -> onEvent(ProfileEditEvent.AddException(from, to, presence, note)) },
            onDismiss = { onEvent(ProfileEditEvent.DismissAddException) },
        )
    }
    state.pendingDelete?.let {
        ConfirmDialog(
            title = stringResource(R.string.exceptions_delete_title),
            text = stringResource(R.string.exceptions_delete_text),
            confirmLabel = stringResource(R.string.exceptions_delete_confirm),
            onConfirm = { onEvent(ProfileEditEvent.ConfirmDelete) },
            onDismiss = { onEvent(ProfileEditEvent.CancelDelete) },
        )
    }
}
