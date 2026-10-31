package de.familyplanner.android.feature.gamification.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.gamification.domain.LoadManagedRewardsUseCase
import de.familyplanner.android.feature.gamification.domain.ManageRewardActions
import de.familyplanner.android.feature.gamification.domain.ManagedRewards
import de.familyplanner.android.feature.gamification.domain.Redemption
import de.familyplanner.android.feature.gamification.domain.Reward
import de.familyplanner.android.feature.gamification.domain.RewardDraft
import de.familyplanner.android.feature.gamification.domain.RewardLimits
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManageRewardsUiState(
    val data: ManagedRewards? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val editing: RewardDraft? = null,
    val rejecting: Redemption? = null,
    val rejectReason: String = "",
    val confirmArchive: Reward? = null,
    val isBusy: Boolean = false,
    /** Validierungsfehler im offenen Dialog. */
    val actionError: AppError? = null,
)

sealed interface ManageRewardsEvent {
    data object Refresh : ManageRewardsEvent

    data object StartCreate : ManageRewardsEvent

    data class StartEdit(
        val reward: Reward,
    ) : ManageRewardsEvent

    data class TitleChanged(
        val text: String,
    ) : ManageRewardsEvent

    data class EmojiChanged(
        val text: String,
    ) : ManageRewardsEvent

    data class PriceChanged(
        val text: String,
    ) : ManageRewardsEvent

    data object SaveReward : ManageRewardsEvent

    data class AskArchive(
        val reward: Reward,
    ) : ManageRewardsEvent

    data object ConfirmArchive : ManageRewardsEvent

    data class Fulfill(
        val redemption: Redemption,
    ) : ManageRewardsEvent

    data class StartReject(
        val redemption: Redemption,
    ) : ManageRewardsEvent

    data class RejectReasonChanged(
        val text: String,
    ) : ManageRewardsEvent

    data object ConfirmReject : ManageRewardsEvent

    data object DismissDialog : ManageRewardsEvent
}

class ManageRewardsViewModel(
    private val load: LoadManagedRewardsUseCase,
    private val actions: ManageRewardActions,
) : ViewModel() {
    private val _state = MutableStateFlow(ManageRewardsUiState())
    val state: StateFlow<ManageRewardsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun onEvent(event: ManageRewardsEvent) {
        when (event) {
            ManageRewardsEvent.Refresh -> refresh()
            ManageRewardsEvent.StartCreate -> _state.update { it.copy(editing = RewardDraft(), actionError = null) }
            is ManageRewardsEvent.StartEdit -> _state.update { it.copy(editing = RewardDraft.of(event.reward), actionError = null) }
            is ManageRewardsEvent.TitleChanged,
            is ManageRewardsEvent.EmojiChanged,
            is ManageRewardsEvent.PriceChanged,
            -> onDraftEvent(event)
            ManageRewardsEvent.SaveReward -> save()
            is ManageRewardsEvent.AskArchive -> _state.update { it.copy(confirmArchive = event.reward) }
            ManageRewardsEvent.ConfirmArchive -> archive()
            is ManageRewardsEvent.Fulfill -> fulfill(event.redemption)
            is ManageRewardsEvent.StartReject ->
                _state.update { it.copy(rejecting = event.redemption, rejectReason = "", actionError = null) }
            is ManageRewardsEvent.RejectReasonChanged -> _state.update { it.copy(rejectReason = event.text.take(RewardLimits.REASON_MAX)) }
            ManageRewardsEvent.ConfirmReject -> reject()
            ManageRewardsEvent.DismissDialog ->
                _state.update { it.copy(editing = null, rejecting = null, confirmArchive = null, actionError = null) }
        }
    }

    private fun onDraftEvent(event: ManageRewardsEvent) {
        when (event) {
            is ManageRewardsEvent.TitleChanged -> editDraft { it.copy(title = event.text.take(RewardLimits.TITLE_MAX)) }
            is ManageRewardsEvent.EmojiChanged -> editDraft { it.copy(emoji = event.text.take(RewardLimits.EMOJI_MAX)) }
            is ManageRewardsEvent.PriceChanged ->
                editDraft { it.copy(priceText = event.text.filter(Char::isDigit).take(RewardLimits.PRICE_DIGITS_MAX)) }
            else -> Unit
        }
    }

    private fun editDraft(change: (RewardDraft) -> RewardDraft) =
        _state.update { s -> s.editing?.let { s.copy(editing = change(it)) } ?: s }

    private fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                when (val result = load()) {
                    is ApiResult.Success -> _state.update { it.copy(isLoading = false, data = result.data) }
                    is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
                }
            }
    }

    private fun save() {
        val draft = _state.value.editing ?: return
        if (_state.value.isBusy || !draft.canSubmit) return
        run { actions.save(draft) }
    }

    private fun archive() {
        val reward = _state.value.confirmArchive ?: return
        run { actions.archive(reward.id) }
    }

    private fun fulfill(redemption: Redemption) = run { actions.fulfill(redemption.id) }

    private fun reject() {
        val current = _state.value
        val redemption = current.rejecting ?: return
        run { actions.reject(redemption.id, current.rejectReason) }
    }

    /** Validierungsfehler bleiben im Dialog, alles andere schließt ihn; bei Konflikt/Nicht gefunden wird neu geladen. */
    private fun run(call: suspend () -> ApiResult<*>) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, actionError = null) }
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> {
                    _state.update { it.closedDialogs().copy(isBusy = false) }
                    refresh()
                }
                is ApiResult.Failure -> onFailure(result.error)
            }
        }
    }

    private fun onFailure(error: AppError) {
        if (error is AppError.Validation) {
            _state.update { it.copy(isBusy = false, actionError = error) }
            return
        }
        if (error is AppError.Conflict || error is AppError.NotFound) refresh()
        _state.update { it.closedDialogs().copy(isBusy = false, error = error) }
    }

    private fun ManageRewardsUiState.closedDialogs() = copy(editing = null, rejecting = null, confirmArchive = null, actionError = null)
}
