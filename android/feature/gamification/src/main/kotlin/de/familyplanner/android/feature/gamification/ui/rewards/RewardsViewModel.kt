package de.familyplanner.android.feature.gamification.ui.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.gamification.domain.ChildRewardActions
import de.familyplanner.android.feature.gamification.domain.ChildRewards
import de.familyplanner.android.feature.gamification.domain.LoadChildRewardsUseCase
import de.familyplanner.android.feature.gamification.domain.Reward
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RewardsUiState(
    val memberId: String?,
    val data: ChildRewards? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val confirmRedeem: Reward? = null,
    val isBusy: Boolean = false,
)

sealed interface RewardsEvent {
    data object Refresh : RewardsEvent

    data class SetGoal(
        val reward: Reward,
    ) : RewardsEvent

    data object ClearGoal : RewardsEvent

    data class AskRedeem(
        val reward: Reward,
    ) : RewardsEvent

    data object ConfirmRedeem : RewardsEvent

    data object DismissRedeem : RewardsEvent
}

class RewardsViewModel(
    sessions: SessionStore,
    private val load: LoadChildRewardsUseCase,
    private val actions: ChildRewardActions,
) : ViewModel() {
    private val _state = MutableStateFlow(RewardsUiState(memberId = sessions.current?.memberId))
    val state: StateFlow<RewardsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun onEvent(event: RewardsEvent) {
        when (event) {
            RewardsEvent.Refresh -> refresh()
            is RewardsEvent.SetGoal -> act { memberId -> actions.setGoal(memberId, event.reward.id) }
            RewardsEvent.ClearGoal -> act { memberId -> actions.clearGoal(memberId) }
            is RewardsEvent.AskRedeem -> _state.update { it.copy(confirmRedeem = event.reward) }
            RewardsEvent.ConfirmRedeem -> redeem()
            RewardsEvent.DismissRedeem -> _state.update { it.copy(confirmRedeem = null) }
        }
    }

    private fun refresh() {
        val memberId = _state.value.memberId
        if (memberId == null) {
            _state.update { it.copy(isLoading = false, error = AppError.Unauthorized) }
            return
        }
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                when (val result = load(memberId)) {
                    is ApiResult.Success -> _state.update { it.copy(isLoading = false, data = result.data) }
                    is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
                }
            }
    }

    private fun redeem() {
        val reward = _state.value.confirmRedeem ?: return
        if (_state.value.isBusy) return
        runAction(closeDialog = true) { actions.redeem(reward.id) }
    }

    private fun act(call: suspend (String) -> ApiResult<*>) {
        val memberId = _state.value.memberId ?: return
        if (_state.value.isBusy) return
        runAction(closeDialog = false) { call(memberId) }
    }

    private fun runAction(
        closeDialog: Boolean,
        call: suspend () -> ApiResult<*>,
    ) {
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val result = call()
            _state.update { it.copy(isBusy = false, confirmRedeem = if (closeDialog) null else it.confirmRedeem) }
            when (result) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> {
                    // Stand hat sich geändert (z. B. Belohnung deaktiviert): erst neu laden, dann den Fehler zeigen.
                    if (result.error is AppError.Conflict) refresh()
                    _state.update { it.copy(error = result.error, confirmRedeem = null) }
                }
            }
        }
    }
}
