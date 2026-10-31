package de.familyplanner.android.feature.gamification.ui.manage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.RewardDraft
import de.familyplanner.android.feature.gamification.domain.RewardLimits

@Composable
fun RewardEditDialog(
    draft: RewardDraft,
    isBusy: Boolean,
    error: AppError?,
    onEvent: (ManageRewardsEvent) -> Unit,
) {
    val priceOutOfRange = draft.price?.let { it !in RewardLimits.PRICE_MIN..RewardLimits.PRICE_MAX } ?: false
    AlertDialog(
        onDismissRequest = { onEvent(ManageRewardsEvent.DismissDialog) },
        title = { Text(stringResource(if (draft.rewardId == null) R.string.game_reward_new else R.string.game_reward_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onEvent(ManageRewardsEvent.TitleChanged(it)) },
                    label = { Text(stringResource(R.string.game_reward_title)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.game_counter, draft.title.length, RewardLimits.TITLE_MAX)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.emoji,
                    onValueChange = { onEvent(ManageRewardsEvent.EmojiChanged(it)) },
                    label = { Text(stringResource(R.string.game_reward_emoji)) },
                    placeholder = { Text(stringResource(R.string.game_reward_emoji_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.priceText,
                    onValueChange = { onEvent(ManageRewardsEvent.PriceChanged(it)) },
                    label = { Text(stringResource(R.string.game_reward_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = priceOutOfRange,
                    supportingText = { if (priceOutOfRange) Text(stringResource(R.string.game_reward_price_range)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ManageRewardsEvent.SaveReward) }, enabled = draft.canSubmit && !isBusy) {
                Text(stringResource(R.string.game_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ManageRewardsEvent.DismissDialog) }) { Text(stringResource(R.string.game_cancel)) }
        },
    )
}
