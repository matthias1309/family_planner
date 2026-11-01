package de.familyplanner.android.feature.gamification.ui.avatar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
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
import de.familyplanner.android.core.ui.avatar.AvatarCredit
import de.familyplanner.android.core.ui.avatar.AvatarImage
import de.familyplanner.android.core.ui.components.ConfirmDialog
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.avatar.PurchaseOption
import de.familyplanner.android.feature.gamification.ui.labelRes
import de.familyplanner.android.feature.gamification.ui.rewardLabel
import org.koin.androidx.compose.koinViewModel

private val PreviewSize = 160.dp

@Composable
fun AvatarEditorRoute(
    onBack: () -> Unit,
    viewModel: AvatarEditorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(AvatarEditorEvent.Refresh) }
    AvatarEditorScreen(state, viewModel::onEvent, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarEditorScreen(
    state: AvatarEditorUiState,
    onEvent: (AvatarEditorEvent) -> Unit,
    onBack: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(AvatarEditorEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.game_back)) } }
            item { Text(stringResource(R.string.game_avatar_title), style = MaterialTheme.typography.headlineMedium) }
            item { ErrorText(state.error) }
            if (state.data != null && state.draft != null) editorItems(state, onEvent)
            item { AvatarCredit() }
        }
    }
    PurchaseDialog(state, onEvent)
}

private fun LazyListScope.editorItems(
    state: AvatarEditorUiState,
    onEvent: (AvatarEditorEvent) -> Unit,
) {
    val draft = state.draft ?: return
    val rubrics = state.rubrics
    val index = state.selectedRubric.coerceIn(0, (rubrics.size - 1).coerceAtLeast(0))
    item { Text(stringResource(R.string.game_rewards_balance, state.balance)) }
    item {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AvatarImage(
                draft,
                state.data
                    ?.profile
                    ?.memberId
                    .orEmpty(),
                size = PreviewSize,
            )
        }
    }
    item {
        PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 0.dp) {
            rubrics.forEachIndexed { i, rubric ->
                Tab(
                    selected = i == index,
                    onClick = { onEvent(AvatarEditorEvent.SelectRubric(i)) },
                    text = { Text(stringResource(rubric.labelRes())) },
                )
            }
        }
    }
    rubrics.getOrNull(index)?.let { rubric ->
        item(key = "grid-$index") { AvatarItemGrid(rubric, draft, state.owned) { onEvent(AvatarEditorEvent.Pick(rubric, it)) } }
    }
    item { ActionRow(state, onEvent) }
}

@Composable
private fun ActionRow(
    state: AvatarEditorUiState,
    onEvent: (AvatarEditorEvent) -> Unit,
) {
    val focused = state.focused
    if (focused != null) {
        Text(
            stringResource(R.string.game_avatar_tile, focused.title, optionLabel(state.purchaseOption)),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    when (val option = state.purchaseOption) {
        is PurchaseOption.Buyable ->
            Button(onClick = { onEvent(AvatarEditorEvent.AskPurchase) }, enabled = !state.isBusy) {
                Text(stringResource(R.string.game_avatar_buy, option.price))
            }
        is PurchaseOption.Missing -> {
            Text(stringResource(R.string.game_rewards_missing, option.points))
            Button(onClick = {}, enabled = false) { Text(stringResource(R.string.game_avatar_buy, focused?.price ?: 0)) }
        }
        PurchaseOption.Wearable, null -> Unit
    }
    Button(onClick = { onEvent(AvatarEditorEvent.Save) }, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.game_avatar_save))
    }
    if (state.saved) Text(stringResource(R.string.game_avatar_saved), color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun optionLabel(option: PurchaseOption?): String =
    when (option) {
        is PurchaseOption.Buyable -> stringResource(R.string.game_points_price, option.price)
        is PurchaseOption.Missing, PurchaseOption.Wearable, null -> stringResource(R.string.game_avatar_owned)
    }

@Composable
private fun PurchaseDialog(
    state: AvatarEditorUiState,
    onEvent: (AvatarEditorEvent) -> Unit,
) {
    val item = state.confirmPurchase ?: return
    val base = stringResource(R.string.game_avatar_buy_text, item.price, state.balance - item.price)
    val goal = state.data?.goal?.reward
    val text =
        if (state.showGoalWarning && goal != null) {
            base + "\n\n" + stringResource(R.string.game_avatar_goal_warning, rewardLabel(goal.emoji, goal.title))
        } else {
            base
        }
    ConfirmDialog(
        title = stringResource(R.string.game_avatar_buy_title, item.title),
        text = text,
        confirmLabel = stringResource(R.string.game_avatar_buy_confirm),
        onConfirm = { onEvent(AvatarEditorEvent.ConfirmPurchase) },
        onDismiss = { onEvent(AvatarEditorEvent.DismissPurchase) },
    )
}
