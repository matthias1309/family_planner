package de.familyplanner.android.feature.auth.ui.pair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.auth.domain.AuthLimits
import de.familyplanner.android.feature.auth.domain.PairDeviceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairUiState(
    val code: String = "",
    val deviceName: String = "",
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
) {
    val canSubmit: Boolean
        get() = AuthLimits.PAIRING_CODE_REGEX.matches(code) && deviceName.isNotBlank() && !isSubmitting
}

sealed interface PairEvent {
    data class CodeChanged(
        val value: String,
    ) : PairEvent

    data class DeviceNameChanged(
        val value: String,
    ) : PairEvent

    data object Submit : PairEvent
}

class PairViewModel(
    private val pairDevice: PairDeviceUseCase,
    defaultDeviceName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(PairUiState(deviceName = defaultDeviceName.take(AuthLimits.DEVICE_NAME_MAX)))
    val state: StateFlow<PairUiState> = _state.asStateFlow()

    fun onEvent(event: PairEvent) {
        when (event) {
            is PairEvent.CodeChanged ->
                _state.update {
                    it.copy(code = event.value.filter(Char::isDigit).take(AuthLimits.PAIRING_CODE_LENGTH), error = null)
                }
            is PairEvent.DeviceNameChanged ->
                _state.update { it.copy(deviceName = event.value.take(AuthLimits.DEVICE_NAME_MAX), error = null) }
            PairEvent.Submit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = pairDevice(current.code, current.deviceName)
            // Bei Erfolg wechselt die Navigation über den SessionStore; der Code wird nicht aufbewahrt.
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(isSubmitting = false, code = "")
                    is ApiResult.Failure -> it.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }
}
