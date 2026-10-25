package de.familyplanner.android.feature.gamification.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatDateTime
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.ui.components.LeaderboardList
import org.koin.androidx.compose.koinViewModel

/** Abschnitt „Punkte“ unter „Familie“ (nur Eltern). */
@Composable
fun FamilyPointsRoute(
    onOpenChild: (memberId: String, name: String) -> Unit,
    viewModel: FamilyPointsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(FamilyPointsEvent.Refresh) }
    FamilyPointsSection(state, viewModel::onEvent, onOpenChild)
}

@Composable
fun FamilyPointsSection(
    state: FamilyPointsUiState,
    onEvent: (FamilyPointsEvent) -> Unit,
    onOpenChild: (memberId: String, name: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.game_family_title), style = MaterialTheme.typography.titleMedium)
        val snapshot = state.snapshot
        if (state.isOffline && snapshot != null) Text(stringResource(R.string.game_offline, formatDateTime(snapshot.syncedAt)))
        ErrorText(state.error)
        if (snapshot != null) {
            LeaderboardList(
                entries = snapshot.leaderboard,
                highlightId = null,
                onClick = if (state.isOffline) null else { child -> onOpenChild(child.memberId, child.name) },
            )
        }
        state.cookingPoints?.let { points ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.game_cooking_points, points), modifier = Modifier.weight(1f))
                TextButton(onClick = { onEvent(FamilyPointsEvent.EditCookingPoints) }, enabled = !state.isOffline) {
                    Text(stringResource(R.string.game_cooking_points_edit))
                }
            }
        }
        HorizontalDivider()
    }
    state.editingCookingPoints?.let { value ->
        CookingPointsDialog(
            value = value,
            isBusy = state.isBusy,
            error = state.actionError,
            onChange = { onEvent(FamilyPointsEvent.CookingPointsChanged(it)) },
            onSave = { onEvent(FamilyPointsEvent.SaveCookingPoints) },
            onDismiss = { onEvent(FamilyPointsEvent.DismissDialog) },
        )
    }
}
