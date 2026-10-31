package de.familyplanner.android.feature.gamification.ui.rewards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ConfirmDialog
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.ChildRewards
import de.familyplanner.android.feature.gamification.domain.Reward
import de.familyplanner.android.feature.gamification.ui.components.RedemptionList
import de.familyplanner.android.feature.gamification.ui.rewardLabel
import org.koin.androidx.compose.koinViewModel

private val CardPadding = 16.dp

@Composable
fun RewardsRoute(
    onBack: () -> Unit,
    viewModel: RewardsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(RewardsEvent.Refresh) }
    RewardsScreen(state, viewModel::onEvent, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    state: RewardsUiState,
    onEvent: (RewardsEvent) -> Unit,
    onBack: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = { onEvent(RewardsEvent.Refresh) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.game_back)) } }
            item { Text(stringResource(R.string.game_rewards_title), style = MaterialTheme.typography.headlineMedium) }
            item { ErrorText(state.error) }
            state.data?.let { data -> rewardItems(data, state.isBusy, onEvent) }
        }
    }
    state.confirmRedeem?.let { reward ->
        ConfirmDialog(
            title = stringResource(R.string.game_redeem_confirm_title, rewardLabel(reward.emoji, reward.title)),
            text = stringResource(R.string.game_redeem_confirm_text, reward.price),
            confirmLabel = stringResource(R.string.game_rewards_redeem),
            onConfirm = { onEvent(RewardsEvent.ConfirmRedeem) },
            onDismiss = { onEvent(RewardsEvent.DismissRedeem) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rewardItems(
    data: ChildRewards,
    isBusy: Boolean,
    onEvent: (RewardsEvent) -> Unit,
) {
    item { Text(stringResource(R.string.game_rewards_balance, data.goal.balance)) }
    if (data.rewards.isEmpty()) item { Text(stringResource(R.string.game_rewards_empty)) }
    data.rewards.forEach { reward ->
        item(key = reward.id) { RewardCard(reward, data.goal.balance, data.goal.reward?.id == reward.id, isBusy, onEvent) }
    }
    item { Text(stringResource(R.string.game_redemptions_title), style = MaterialTheme.typography.titleMedium) }
    item { RedemptionList(data.redemptions) }
}

@Composable
private fun RewardCard(
    reward: Reward,
    balance: Int,
    isGoal: Boolean,
    isBusy: Boolean,
    onEvent: (RewardsEvent) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(CardPadding)) {
            Text(rewardLabel(reward.emoji, reward.title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.game_points_price, reward.price))
            LinearProgressIndicator(
                progress = { (balance.toFloat() / reward.price.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (isGoal) Text(stringResource(R.string.game_rewards_is_goal), color = MaterialTheme.colorScheme.primary)
            if (balance < reward.price) Text(stringResource(R.string.game_rewards_missing, reward.price - balance))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onEvent(if (isGoal) RewardsEvent.ClearGoal else RewardsEvent.SetGoal(reward)) },
                    enabled = !isBusy,
                ) {
                    Text(stringResource(if (isGoal) R.string.game_rewards_clear_goal else R.string.game_rewards_set_goal))
                }
                Button(onClick = { onEvent(RewardsEvent.AskRedeem(reward)) }, enabled = balance >= reward.price && !isBusy) {
                    Text(stringResource(R.string.game_rewards_redeem))
                }
            }
        }
    }
}
