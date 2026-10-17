package de.familyplanner.android.feature.auth.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.auth.domain.CreateMemberUseCase
import de.familyplanner.android.feature.auth.domain.CreatePairingCodeUseCase
import de.familyplanner.android.feature.auth.domain.FamilyDevice
import de.familyplanner.android.feature.auth.domain.FamilyMember
import de.familyplanner.android.feature.auth.domain.IssuedPairingCode
import de.familyplanner.android.feature.auth.domain.LoadFamilyUseCase
import de.familyplanner.android.feature.auth.domain.RevokeDeviceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IssuedCodeDialog(
    val memberName: String,
    val code: IssuedPairingCode,
)

data class FamilyUiState(
    val members: List<FamilyMember> = emptyList(),
    val devices: List<FamilyDevice> = emptyList(),
    val ownDeviceId: String? = null,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val showAddMember: Boolean = false,
    val addMemberError: AppError? = null,
    val isBusy: Boolean = false,
    val issuedCode: IssuedCodeDialog? = null,
    val pendingRevoke: FamilyDevice? = null,
) {
    fun memberName(id: String): String = members.firstOrNull { it.id == id }?.name.orEmpty()

    val revokesOwnDevice: Boolean get() = pendingRevoke != null && pendingRevoke.id == ownDeviceId
}

sealed interface FamilyEvent {
    data object Refresh : FamilyEvent

    data object ShowAddMember : FamilyEvent

    data object DismissAddMember : FamilyEvent

    data class AddMember(
        val name: String,
        val role: Role,
    ) : FamilyEvent

    data class RequestCode(
        val member: FamilyMember,
    ) : FamilyEvent

    data object DismissCode : FamilyEvent

    data class RequestRevoke(
        val device: FamilyDevice,
    ) : FamilyEvent

    data object CancelRevoke : FamilyEvent

    data object ConfirmRevoke : FamilyEvent
}

class FamilyViewModel(
    sessions: SessionStore,
    private val loadFamily: LoadFamilyUseCase,
    private val createMember: CreateMemberUseCase,
    private val createPairingCode: CreatePairingCodeUseCase,
    private val revokeDevice: RevokeDeviceUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(FamilyUiState(ownDeviceId = sessions.current?.deviceId))
    val state: StateFlow<FamilyUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: FamilyEvent) {
        when (event) {
            FamilyEvent.Refresh -> refresh()
            FamilyEvent.ShowAddMember -> _state.update { it.copy(showAddMember = true, addMemberError = null) }
            FamilyEvent.DismissAddMember -> _state.update { it.copy(showAddMember = false, addMemberError = null) }
            is FamilyEvent.AddMember -> addMember(event.name, event.role)
            is FamilyEvent.RequestCode -> requestCode(event.member)
            FamilyEvent.DismissCode -> _state.update { it.copy(issuedCode = null) }
            is FamilyEvent.RequestRevoke -> _state.update { it.copy(pendingRevoke = event.device) }
            FamilyEvent.CancelRevoke -> _state.update { it.copy(pendingRevoke = null) }
            FamilyEvent.ConfirmRevoke -> confirmRevoke()
        }
    }

    private fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = loadFamily()) {
                is ApiResult.Success ->
                    _state.update {
                        it.copy(isLoading = false, members = result.data.members, devices = result.data.devices)
                    }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    private fun addMember(
        name: String,
        role: Role,
    ) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, addMemberError = null) }
        viewModelScope.launch {
            when (val result = createMember(name, role)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isBusy = false, showAddMember = false) }
                    refresh()
                }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, addMemberError = result.error) }
            }
        }
    }

    private fun requestCode(member: FamilyMember) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = createPairingCode(member.id)) {
                is ApiResult.Success ->
                    _state.update { it.copy(isBusy = false, issuedCode = IssuedCodeDialog(member.name, result.data)) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error) }
            }
        }
    }

    private fun confirmRevoke() {
        val device = _state.value.pendingRevoke ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, pendingRevoke = null) }
        viewModelScope.launch {
            when (val result = revokeDevice(device.id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isBusy = false) }
                    refresh()
                }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error) }
            }
        }
    }
}
