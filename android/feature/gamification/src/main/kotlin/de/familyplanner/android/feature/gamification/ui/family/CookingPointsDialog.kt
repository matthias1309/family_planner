package de.familyplanner.android.feature.gamification.ui.family

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.PointsLimits

@Composable
fun CookingPointsDialog(
    value: Int,
    isBusy: Boolean,
    error: AppError?,
    onChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_cooking_points_dialog_title)) },
        text = {
            Column {
                PointsStepper(value, PointsLimits.COOKING_POINTS_MIN, PointsLimits.COOKING_POINTS_MAX, onChange, enabled = !isBusy)
                Text(stringResource(R.string.game_cooking_points_hint))
                ErrorText(error)
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = !isBusy) { Text(stringResource(R.string.game_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_cancel)) } },
    )
}
