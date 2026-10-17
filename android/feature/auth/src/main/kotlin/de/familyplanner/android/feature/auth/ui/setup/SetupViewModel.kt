package de.familyplanner.android.feature.auth.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.auth.domain.AuthLimits
import de.familyplanner.android.feature.auth.domain.SetupFamilyUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val setupToken: String = "",
    val parentName: String = "",
    val deviceName: String = "",
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
) {
    val canSubmit: Boolean
        get() =
            setupToken.length >= AuthLimits.SETUP_TOKEN_MIN &&
                parentName.isNotBlank() &&
                deviceName.isNotBlank() &&
                !isSubmitting

    /** Server meldet 409: Familie existiert schon → Hinweis + Weg zur Kopplung. */
    val alreadySetUp: Boolean get() = error is AppError.Conflict
}

sealed interface SetupEvent {
    data class TokenChanged(
        val value: String,
    ) : SetupEvent

    data class ParentNameChanged(
        val value: String,
    ) : SetupEvent

    data class DeviceNameChanged(
        val value: String,
    ) : SetupEvent

    data object Submit : SetupEvent
}

class SetupViewModel(
    private val setupFamily: SetupFamilyUseCase,
    defaultDeviceName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState(deviceName = defaultDeviceName.take(AuthLimits.DEVICE_NAME_MAX)))
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun onEvent(event: SetupEvent) {
        when (event) {
            is SetupEvent.TokenChanged -> _state.update { it.copy(setupToken = event.value, error = null) }
            is SetupEvent.ParentNameChanged ->
                _state.update { it.copy(parentName = event.value.take(AuthLimits.NAME_MAX), error = null) }
            is SetupEvent.DeviceNameChanged ->
                _state.update { it.copy(deviceName = event.value.take(AuthLimits.DEVICE_NAME_MAX), error = null) }
            SetupEvent.Submit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = setupFamily(current.setupToken, current.parentName, current.deviceName)
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(isSubmitting = false, setupToken = "")
                    is ApiResult.Failure -> it.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }
}
