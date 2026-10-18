package de.familyplanner.android.feature.meals.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.attendance.ChildProfile
import de.familyplanner.android.feature.meals.ui.patternSummary
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfilesRoute(
    onEdit: (memberId: String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<ProfilesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Nach dem Zurückkehren aus dem Editor sind Muster und Kochen-Schalter evtl. geändert.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(ProfilesEvent.Refresh)
        onPauseOrDispose {}
    }
    ProfilesScreen(state = state, onEvent = viewModel::onEvent, onEdit = onEdit, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    state: ProfilesUiState,
    onEvent: (ProfilesEvent) -> Unit,
    onEdit: (memberId: String) -> Unit,
    onBack: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(ProfilesEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.headlineMedium) }
            item { Text(stringResource(R.string.profiles_hint), style = MaterialTheme.typography.bodyMedium) }
            item { ErrorText(state.error) }
            if (state.profiles.isEmpty() && !state.isLoading && state.error == null) {
                item { Text(stringResource(R.string.profiles_empty)) }
            }
            items(state.profiles, key = { it.memberId }) { ProfileRow(it) { onEdit(it.memberId) } }
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
        }
    }
}

@Composable
private fun ProfileRow(
    child: ChildProfile,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(child.name, style = MaterialTheme.typography.titleMedium)
            val cooks = stringResource(if (child.profile.cooks) R.string.profiles_cooks else R.string.profiles_cooks_not)
            Text("$cooks · ${patternSummary(child.profile.pattern)}", style = MaterialTheme.typography.bodyMedium)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall)
    }
}
