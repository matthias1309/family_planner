package de.familyplanner.android.feature.auth.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.components.SubmitButton
import de.familyplanner.android.feature.auth.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun SetupRoute(
    onBack: () -> Unit,
    onPair: () -> Unit,
) {
    val viewModel = koinViewModel<SetupViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SetupScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, onPair = onPair)
}

@Composable
fun SetupScreen(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
    onBack: () -> Unit,
    onPair: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.setupToken,
            onValueChange = { onEvent(SetupEvent.TokenChanged(it)) },
            label = { Text(stringResource(R.string.setup_token)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.parentName,
            onValueChange = { onEvent(SetupEvent.ParentNameChanged(it)) },
            label = { Text(stringResource(R.string.setup_parent_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.deviceName,
            onValueChange = { onEvent(SetupEvent.DeviceNameChanged(it)) },
            label = { Text(stringResource(R.string.setup_device_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.alreadySetUp) {
            Text(stringResource(R.string.setup_already), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onPair) { Text(stringResource(R.string.welcome_pair)) }
        } else {
            ErrorText(state.error)
        }
        SubmitButton(
            text = stringResource(R.string.setup_submit),
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            onClick = { onEvent(SetupEvent.Submit) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}
