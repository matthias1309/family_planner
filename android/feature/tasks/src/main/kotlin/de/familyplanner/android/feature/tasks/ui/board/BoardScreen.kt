package de.familyplanner.android.feature.tasks.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ConfirmDialog
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatDateTime
import de.familyplanner.android.feature.tasks.R
import de.familyplanner.android.feature.tasks.domain.board.CardAction
import org.koin.androidx.compose.koinViewModel

private val ScreenPadding = 24.dp
private val FabClearance = 80.dp

@Composable
fun BoardRoute(
    onOpenCatalog: () -> Unit,
    onAssign: (String?) -> Unit,
    onOpenHistory: (memberId: String, name: String) -> Unit,
) {
    val viewModel = koinViewModel<BoardViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Zuweisen, Hinweise und andere Elternteile ändern das Board: beim Zurückkehren neu laden.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(BoardEvent.Refresh)
        onPauseOrDispose {}
    }
    BoardScreen(state, viewModel::onEvent, onOpenCatalog, onAssign, onOpenHistory)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    state: BoardUiState,
    onEvent: (BoardEvent) -> Unit,
    onOpenCatalog: () -> Unit,
    onAssign: (String?) -> Unit,
    onOpenHistory: (memberId: String, name: String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { onEvent(BoardEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                BoardHeader(state, onOpenCatalog)
                BoardTabs(state, onEvent)
                BoardContent(state, onEvent, onOpenHistory)
            }
        }
        if (state.isParent) {
            ExtendedFloatingActionButton(
                onClick = { onAssign((state.selectedTabContent as? BoardTab.Lane)?.lane?.memberId) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.tasks_assign_fab)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
    BoardDialogs(state, onEvent)
}

@Composable
private fun BoardHeader(
    state: BoardUiState,
    onOpenCatalog: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = ScreenPadding, end = 8.dp, top = 16.dp),
    ) {
        Text(stringResource(R.string.tasks_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        if (state.isParent) {
            IconButton(onClick = onOpenCatalog) {
                Icon(Icons.Filled.List, contentDescription = stringResource(R.string.tasks_catalog_open))
            }
        }
    }
}

@Composable
private fun BoardTabs(
    state: BoardUiState,
    onEvent: (BoardEvent) -> Unit,
) {
    val tabs = state.tabs
    if (tabs.isEmpty()) return
    PrimaryScrollableTabRow(selectedTabIndex = state.selectedIndex, edgePadding = 8.dp) {
        tabs.forEachIndexed { index, tab ->
            val title =
                when (tab) {
                    BoardTab.Review -> stringResource(R.string.tasks_tab_review, state.reviewCount)
                    is BoardTab.Lane -> tab.lane.name
                }
            Tab(selected = index == state.selectedIndex, onClick = { onEvent(BoardEvent.SelectTab(index)) }, text = { Text(title) })
        }
    }
}

@Composable
private fun BoardContent(
    state: BoardUiState,
    onEvent: (BoardEvent) -> Unit,
    onOpenHistory: (memberId: String, name: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isOffline && state.syncedAt != null) {
            item {
                Text(
                    stringResource(R.string.tasks_offline, formatDateTime(state.syncedAt)),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        state.error?.let { item { ErrorText(it) } }
        state.actionError?.let { item { ErrorText(it) } }
        when (val tab = state.selectedTabContent) {
            null -> if (!state.isLoading && state.error == null) item { Text(stringResource(R.string.tasks_no_children)) }
            BoardTab.Review -> reviewSection(state, onEvent)
            is BoardTab.Lane -> laneSections(tab.lane, state, onEvent) { onOpenHistory(it.memberId, it.name) }
        }
        // Platz unter der letzten Karte, damit der Zuweisen-Knopf nichts verdeckt.
        item { Box(modifier = Modifier.padding(bottom = FabClearance)) }
    }
}

@Composable
private fun BoardDialogs(
    state: BoardUiState,
    onEvent: (BoardEvent) -> Unit,
) {
    state.rejecting?.let { card ->
        RejectDialog(
            title = card.title,
            reason = state.rejectReason,
            onReasonChange = { onEvent(BoardEvent.RejectReasonChanged(it)) },
            isBusy = state.isBusy,
            onConfirm = { onEvent(BoardEvent.ConfirmReject) },
            onDismiss = { onEvent(BoardEvent.DismissDialog) },
        )
    }
    state.confirming?.let { (card, action) ->
        val accept = action == CardAction.ACCEPT_DIRECTLY
        ConfirmDialog(
            title = stringResource(if (accept) R.string.tasks_accept_directly_title else R.string.tasks_remove_title),
            text = stringResource(if (accept) R.string.tasks_accept_directly_text else R.string.tasks_remove_text, card.title),
            confirmLabel = stringResource(if (accept) R.string.tasks_action_accept_directly else R.string.tasks_action_remove),
            onConfirm = { onEvent(BoardEvent.ConfirmAction) },
            onDismiss = { onEvent(BoardEvent.DismissDialog) },
        )
    }
}
