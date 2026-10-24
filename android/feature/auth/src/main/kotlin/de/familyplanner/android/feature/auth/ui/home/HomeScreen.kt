package de.familyplanner.android.feature.auth.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.feature.auth.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(todayContent: @Composable () -> Unit = {}) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, todayContent = todayContent)
}

/** „Heute“: Begrüßung, Rolle, Offline-Hinweis; [todayContent] nimmt die Inhalte anderer Module auf. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    todayContent: @Composable () -> Unit = {},
) {
    val session = state.session ?: return
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.home_greeting, session.memberName), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(if (session.role == Role.PARENT) R.string.home_role_parent else R.string.home_role_child),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.isOffline) {
            Text(stringResource(R.string.home_offline), color = MaterialTheme.colorScheme.tertiary)
        }
        todayContent()
    }
}
