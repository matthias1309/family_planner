package de.familyplanner.android.feature.gamification.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatDateTime
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.RankedChild
import de.familyplanner.android.feature.gamification.ui.components.BadgeHeader
import de.familyplanner.android.feature.gamification.ui.components.BadgeList
import de.familyplanner.android.feature.gamification.ui.components.LeaderboardList
import de.familyplanner.android.feature.gamification.ui.components.LedgerList
import de.familyplanner.android.feature.gamification.ui.components.NewBadgesDialog
import de.familyplanner.android.feature.gamification.ui.components.StatTiles
import org.koin.androidx.compose.koinViewModel

@Composable
fun MeRoute(
    onOpenBadges: (memberId: String, name: String) -> Unit,
    viewModel: MeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(MeEvent.Refresh) }
    MeScreen(state, viewModel::onEvent, onOpenBadges)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    state: MeUiState,
    onEvent: (MeEvent) -> Unit,
    onOpenBadges: (memberId: String, name: String) -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = { onEvent(MeEvent.Refresh) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(stringResource(R.string.game_me_title), style = MaterialTheme.typography.headlineMedium) }
            val snapshot = state.snapshot
            if (state.isOffline && snapshot != null) {
                item { Text(stringResource(R.string.game_offline, formatDateTime(snapshot.syncedAt))) }
            }
            item { ErrorText(state.error) }
            if (snapshot != null) {
                snapshot.account?.let { account -> item { StatTiles(account.collected, account.balance) } }
                state.myRank?.let { item { Text(stringResource(R.string.game_rank_of, it.rank, snapshot.leaderboard.size)) } }
                item { Text(stringResource(R.string.game_leaderboard), style = MaterialTheme.typography.titleMedium) }
                item {
                    val onChildClick: ((RankedChild) -> Unit)? =
                        if (state.isOffline) null else { child -> onOpenBadges(child.memberId, child.name) }
                    LeaderboardList(snapshot.leaderboard, highlightId = state.memberId, onClick = onChildClick)
                }
                snapshot.badges?.let { badges ->
                    item { BadgeHeader(badges) }
                    item { BadgeList(badges.items) }
                }
                item { Text(stringResource(R.string.game_history), style = MaterialTheme.typography.titleMedium) }
                item { LedgerList(snapshot.account?.entries.orEmpty()) }
            }
        }
    }
    if (state.newBadges.isNotEmpty()) NewBadgesDialog(state.newBadges) { onEvent(MeEvent.DismissNewBadges) }
}
