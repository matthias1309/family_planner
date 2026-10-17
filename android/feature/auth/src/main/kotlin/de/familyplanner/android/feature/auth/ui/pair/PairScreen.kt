package de.familyplanner.android.feature.auth.ui.pair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.familyplanner.android.core.ui.components.ErrorText
import de.familyplanner.android.core.ui.components.SubmitButton
import de.familyplanner.android.feature.auth.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun PairRoute(onBack: () -> Unit) {
    val viewModel = koinViewModel<PairViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    PairScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@Composable
fun PairScreen(
    state: PairUiState,
    onEvent: (PairEvent) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.code,
            onValueChange = { onEvent(PairEvent.CodeChanged(it)) },
            label = { Text(stringResource(R.string.pair_code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.deviceName,
            onValueChange = { onEvent(PairEvent.DeviceNameChanged(it)) },
            label = { Text(stringResource(R.string.pair_device_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(state.error)
        SubmitButton(
            text = stringResource(R.string.pair_submit),
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            onClick = { onEvent(PairEvent.Submit) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}
