package de.familyplanner.android.feature.auth.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ConfirmDialog
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.formatDateTime
import de.familyplanner.android.feature.auth.R
import de.familyplanner.android.feature.auth.domain.FamilyDevice
import de.familyplanner.android.feature.auth.domain.FamilyMember
import org.koin.androidx.compose.koinViewModel

@Composable
fun FamilyRoute(onBack: (() -> Unit)? = null) {
    val viewModel = koinViewModel<FamilyViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    FamilyScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    state: FamilyUiState,
    onEvent: (FamilyEvent) -> Unit,
    onBack: (() -> Unit)?,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(FamilyEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(stringResource(R.string.family_title), style = MaterialTheme.typography.headlineMedium) }
            item { ErrorText(state.error) }
            item { Text(stringResource(R.string.family_members), style = MaterialTheme.typography.titleMedium) }
            items(state.members, key = { "m-${it.id}" }) { member -> MemberRow(member) { onEvent(FamilyEvent.RequestCode(member)) } }
            item {
                Button(onClick = { onEvent(FamilyEvent.ShowAddMember) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.family_add_member))
                }
            }
            item { Text(stringResource(R.string.family_devices), style = MaterialTheme.typography.titleMedium) }
            items(state.devices, key = { "d-${it.id}" }) { device ->
                DeviceRow(device, state.memberName(device.memberId)) { onEvent(FamilyEvent.RequestRevoke(device)) }
            }
            if (onBack != null) {
                item { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
            }
        }
    }
    FamilyDialogsHost(state, onEvent)
}

@Composable
private fun FamilyDialogsHost(
    state: FamilyUiState,
    onEvent: (FamilyEvent) -> Unit,
) {
    if (state.showAddMember) {
        AddMemberDialog(
            error = state.addMemberError,
            isBusy = state.isBusy,
            onSubmit = { name, role -> onEvent(FamilyEvent.AddMember(name, role)) },
            onDismiss = { onEvent(FamilyEvent.DismissAddMember) },
        )
    }
    state.issuedCode?.let { PairingCodeDialog(it) { onEvent(FamilyEvent.DismissCode) } }
    state.pendingRevoke?.let { device ->
        ConfirmDialog(
            title = stringResource(R.string.revoke_title),
            text =
                if (state.revokesOwnDevice) {
                    stringResource(R.string.revoke_own)
                } else {
                    stringResource(R.string.revoke_text, device.name)
                },
            confirmLabel = stringResource(R.string.revoke_confirm),
            onConfirm = { onEvent(FamilyEvent.ConfirmRevoke) },
            onDismiss = { onEvent(FamilyEvent.CancelRevoke) },
        )
    }
}

@Composable
private fun MemberRow(
    member: FamilyMember,
    onCode: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(member.name, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(member.role.labelRes()), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onCode) { Text(stringResource(R.string.family_code_button)) }
    }
}

@Composable
private fun DeviceRow(
    device: FamilyDevice,
    memberName: String,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.family_device_line, device.name, memberName), style = MaterialTheme.typography.bodyLarge)
            val seen = device.lastSeenAt?.let { stringResource(R.string.family_last_seen, formatDateTime(it)) }
            Text(seen ?: stringResource(R.string.family_never_seen), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.family_remove_device))
        }
    }
}
