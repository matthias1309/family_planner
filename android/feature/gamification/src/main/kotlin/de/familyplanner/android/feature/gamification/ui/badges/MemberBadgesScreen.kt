package de.familyplanner.android.feature.gamification.ui.badges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.ui.components.BadgeHeader
import de.familyplanner.android.feature.gamification.ui.components.BadgeList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MemberBadgesRoute(
    args: MemberBadgesArgs,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<MemberBadgesViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(MemberBadgesEvent.Refresh) }
    MemberBadgesScreen(state, viewModel::onEvent, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberBadgesScreen(
    state: MemberBadgesUiState,
    onEvent: (MemberBadgesEvent) -> Unit,
    onBack: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(MemberBadgesEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.game_back)) } }
            item {
                Text(stringResource(R.string.game_member_badges_title, state.args.name), style = MaterialTheme.typography.headlineMedium)
            }
            item { ErrorText(state.error) }
            state.overview?.let { overview ->
                item { BadgeHeader(overview) }
                item { BadgeList(overview.items) }
            }
        }
    }
}
