package de.familyplanner.android.feature.gamification.ui.manage

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
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.Redemption
import de.familyplanner.android.feature.gamification.domain.RewardLimits
import de.familyplanner.android.feature.gamification.domain.validateRejection
import de.familyplanner.android.feature.gamification.ui.rewardLabel

@Composable
fun RejectRedemptionDialog(
    redemption: Redemption,
    reason: String,
    isBusy: Boolean,
    error: AppError?,
    onEvent: (ManageRewardsEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ManageRewardsEvent.DismissDialog) },
        title = { Text(stringResource(R.string.game_reject_title, rewardLabel(redemption.emoji, redemption.title))) },
        text = {
            Column {
                Text(stringResource(R.string.game_reject_hint))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { onEvent(ManageRewardsEvent.RejectReasonChanged(it)) },
                    label = { Text(stringResource(R.string.game_reject_reason)) },
                    supportingText = { Text(stringResource(R.string.game_counter, reason.length, RewardLimits.REASON_MAX)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ManageRewardsEvent.ConfirmReject) }, enabled = validateRejection(reason) == null && !isBusy) {
                Text(stringResource(R.string.game_manage_reject))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ManageRewardsEvent.DismissDialog) }) { Text(stringResource(R.string.game_cancel)) }
        },
    )
}
