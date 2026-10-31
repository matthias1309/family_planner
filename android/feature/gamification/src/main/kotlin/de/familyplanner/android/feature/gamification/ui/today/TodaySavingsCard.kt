package de.familyplanner.android.feature.gamification.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.feature.gamification.ui.components.SavingsGoalCard
import org.koin.androidx.compose.koinViewModel

@Composable
fun TodaySavingsRoute(onOpenRewards: () -> Unit) {
    val viewModel = koinViewModel<TodaySavingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodaySavingsCard(state, onOpenRewards)
}

/** Sparziel auf „Heute“; ohne gesetztes Ziel nichts. */
@Composable
fun TodaySavingsCard(
    state: TodaySavingsUiState,
    onOpenRewards: () -> Unit,
) {
    if (state.goal?.reward == null) return
    SavingsGoalCard(state.goal, onClick = onOpenRewards)
}
