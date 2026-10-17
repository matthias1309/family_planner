package de.familyplanner.android.feature.auth.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.feature.auth.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(
    onManageFamily: () -> Unit,
    homeActions: @Composable () -> Unit = {},
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, onManageFamily = onManageFamily, homeActions = homeActions)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onManageFamily: () -> Unit,
    homeActions: @Composable () -> Unit = {},
) {
    val session = state.session ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_greeting, session.memberName), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(if (session.role == Role.PARENT) R.string.home_role_parent else R.string.home_role_child),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.home_more))
        if (state.isOffline) {
            Text(stringResource(R.string.home_offline), color = MaterialTheme.colorScheme.tertiary)
        }
        homeActions()
        if (state.canManageFamily) {
            Button(onClick = onManageFamily, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_manage_family))
            }
        }
    }
}
