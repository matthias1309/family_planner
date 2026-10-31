package de.familyplanner.android.feature.gamification.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ConfirmDialog
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.components.SubmitButton
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.ManagedRewards
import de.familyplanner.android.feature.gamification.domain.Redemption
import de.familyplanner.android.feature.gamification.domain.Reward
import de.familyplanner.android.feature.gamification.ui.rewardLabel
import org.koin.androidx.compose.koinViewModel

@Composable
fun ManageRewardsRoute(
    onBack: () -> Unit,
    viewModel: ManageRewardsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(ManageRewardsEvent.Refresh) }
    ManageRewardsScreen(state, viewModel::onEvent, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageRewardsScreen(
    state: ManageRewardsUiState,
    onEvent: (ManageRewardsEvent) -> Unit,
    onBack: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(ManageRewardsEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.game_back)) } }
            item { Text(stringResource(R.string.game_manage_title), style = MaterialTheme.typography.headlineMedium) }
            item { ErrorText(state.error) }
            state.data?.let { manageItems(it, state.isBusy, onEvent) }
            item {
                SubmitButton(
                    text = stringResource(R.string.game_manage_create),
                    enabled = state.data != null,
                    loading = false,
                    onClick = { onEvent(ManageRewardsEvent.StartCreate) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    ManageDialogs(state, onEvent)
}

private fun LazyListScope.manageItems(
    data: ManagedRewards,
    isBusy: Boolean,
    onEvent: (ManageRewardsEvent) -> Unit,
) {
    item { Text(stringResource(R.string.game_manage_pending), style = MaterialTheme.typography.titleMedium) }
    if (data.pending.isEmpty()) item { Text(stringResource(R.string.game_manage_pending_empty)) }
    data.pending.forEach { redemption ->
        item(key = "pending-${redemption.id}") { PendingRow(redemption, data.names[redemption.memberId], isBusy, onEvent) }
    }
    item { Text(stringResource(R.string.game_manage_rewards), style = MaterialTheme.typography.titleMedium) }
    if (data.rewards.isEmpty()) item { Text(stringResource(R.string.game_manage_rewards_empty)) }
    data.rewards.forEach { reward ->
        item(key = "reward-${reward.id}") { RewardRow(reward, onEvent) }
    }
}

@Composable
private fun PendingRow(
    redemption: Redemption,
    childName: String?,
    isBusy: Boolean,
    onEvent: (ManageRewardsEvent) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(
                R.string.game_manage_pending_row,
                childName ?: stringResource(R.string.game_child_fallback),
                rewardLabel(redemption.emoji, redemption.title),
                redemption.price,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onEvent(ManageRewardsEvent.StartReject(redemption)) }, enabled = !isBusy) {
                Text(stringResource(R.string.game_manage_reject))
            }
            Button(onClick = { onEvent(ManageRewardsEvent.Fulfill(redemption)) }, enabled = !isBusy) {
                Text(stringResource(R.string.game_manage_fulfill))
            }
        }
    }
}

@Composable
private fun RewardRow(
    reward: Reward,
    onEvent: (ManageRewardsEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onEvent(ManageRewardsEvent.StartEdit(reward)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rewardLabel(reward.emoji, reward.title), modifier = Modifier.weight(1f))
        Text(stringResource(R.string.game_points_price, reward.price))
        TextButton(onClick = { onEvent(ManageRewardsEvent.AskArchive(reward)) }) { Text(stringResource(R.string.game_manage_archive)) }
    }
}

@Composable
private fun ManageDialogs(
    state: ManageRewardsUiState,
    onEvent: (ManageRewardsEvent) -> Unit,
) {
    state.editing?.let { RewardEditDialog(it, state.isBusy, state.actionError, onEvent) }
    state.rejecting?.let { RejectRedemptionDialog(it, state.rejectReason, state.isBusy, state.actionError, onEvent) }
    state.confirmArchive?.let { reward ->
        ConfirmDialog(
            title = stringResource(R.string.game_archive_title, rewardLabel(reward.emoji, reward.title)),
            text = stringResource(R.string.game_archive_text),
            confirmLabel = stringResource(R.string.game_manage_archive),
            onConfirm = { onEvent(ManageRewardsEvent.ConfirmArchive) },
            onDismiss = { onEvent(ManageRewardsEvent.DismissDialog) },
        )
    }
}
