package de.familyplanner.android.feature.gamification.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.avatar.AvatarLook
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarActions
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarEditorData
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarItem
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarRubric
import de.familyplanner.android.feature.gamification.domain.avatar.LoadAvatarEditorUseCase
import de.familyplanner.android.feature.gamification.domain.avatar.PurchaseOption
import de.familyplanner.android.feature.gamification.domain.avatar.canSave
import de.familyplanner.android.feature.gamification.domain.avatar.goalWarning
import de.familyplanner.android.feature.gamification.domain.avatar.purchaseOption
import de.familyplanner.android.feature.gamification.domain.avatar.rubrics
import de.familyplanner.android.feature.gamification.domain.avatar.wearing
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AvatarEditorUiState(
    val memberId: String?,
    val name: String = "",
    val data: AvatarEditorData? = null,
    val draft: AvatarLook? = null,
    val owned: Set<String> = emptySet(),
    val balance: Int = 0,
    val selectedRubric: Int = 0,
    val focused: AvatarItem? = null,
    val confirmPurchase: AvatarItem? = null,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val error: AppError? = null,
    val saved: Boolean = false,
) {
    val rubrics: List<AvatarRubric> get() = data?.catalog?.rubrics().orEmpty()

    val canSave: Boolean
        get() {
            val loaded = data ?: return false
            val look = draft ?: return false
            return !isBusy && canSave(look, loaded.profile.look, loaded.catalog, owned)
        }

    val purchaseOption: PurchaseOption? get() = focused?.let { purchaseOption(it, owned, balance) }

    val showGoalWarning: Boolean get() = confirmPurchase?.let { goalWarning(data?.goal, balance, it.price) } ?: false
}

sealed interface AvatarEditorEvent {
    data object Refresh : AvatarEditorEvent

    data class SelectRubric(
        val index: Int,
    ) : AvatarEditorEvent

    /** [item] `null` = „Nichts“ in einem optionalen Slot. */
    data class Pick(
        val rubric: AvatarRubric,
        val item: AvatarItem?,
    ) : AvatarEditorEvent

    data object AskPurchase : AvatarEditorEvent

    data object ConfirmPurchase : AvatarEditorEvent

    data object DismissPurchase : AvatarEditorEvent

    data object Save : AvatarEditorEvent
}

class AvatarEditorViewModel(
    private val sessions: SessionStore,
    private val load: LoadAvatarEditorUseCase,
    private val actions: AvatarActions,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            AvatarEditorUiState(memberId = sessions.current?.memberId, name = sessions.current?.memberName.orEmpty()),
        )
    val state: StateFlow<AvatarEditorUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun onEvent(event: AvatarEditorEvent) {
        when (event) {
            AvatarEditorEvent.Refresh -> refresh()
            is AvatarEditorEvent.SelectRubric -> _state.update { it.copy(selectedRubric = event.index) }
            is AvatarEditorEvent.Pick -> pick(event.rubric, event.item)
            AvatarEditorEvent.AskPurchase -> askPurchase()
            AvatarEditorEvent.ConfirmPurchase -> purchase()
            AvatarEditorEvent.DismissPurchase -> _state.update { it.copy(confirmPurchase = null) }
            AvatarEditorEvent.Save -> save()
        }
    }

    /** [keepError]: Fehler einer Aktion, der den Neuladen-Vorgang überdauert (z. B. abgelehnter Kauf). */
    private fun refresh(keepError: AppError? = null) {
        val memberId = _state.value.memberId
        if (memberId == null || sessions.current?.role != Role.CHILD) {
            _state.update { it.copy(isLoading = false, error = if (memberId == null) AppError.Unauthorized else AppError.Forbidden) }
            return
        }
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = keepError) }
        loadJob =
            viewModelScope.launch {
                when (val result = load(memberId)) {
                    is ApiResult.Success ->
                        _state.update {
                            // Ein vorhandener Entwurf bleibt, etwa nach einem abgelehnten Kauf.
                            it.copy(
                                isLoading = false,
                                data = result.data,
                                draft = it.draft ?: result.data.startLook,
                                owned = result.data.profile.owned,
                                balance = result.data.goal.balance,
                            )
                        }
                    is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
                }
            }
    }

    private fun pick(
        rubric: AvatarRubric,
        item: AvatarItem?,
    ) {
        _state.update { current ->
            val look = current.draft ?: return@update current
            current.copy(draft = look.wearing(rubric.slot, item?.id), focused = item, saved = false)
        }
    }

    private fun askPurchase() {
        _state.update { current ->
            val item = current.focused
            if (item != null && current.purchaseOption is PurchaseOption.Buyable) current.copy(confirmPurchase = item) else current
        }
    }

    private fun purchase() {
        val item = _state.value.confirmPurchase ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = actions.purchase(item.id)) {
                is ApiResult.Success ->
                    _state.update {
                        it.copy(isBusy = false, confirmPurchase = null, owned = it.owned + item.id, balance = result.data.balance)
                    }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isBusy = false, confirmPurchase = null, error = result.error) }
                    // Stand hat sich geändert (schon gekauft, Guthaben zu knapp): neu laden, der Entwurf bleibt.
                    if (result.error is AppError.Conflict) refresh(keepError = result.error)
                }
            }
        }
    }

    private fun save() {
        val memberId = _state.value.memberId ?: return
        val look = _state.value.draft ?: return
        if (!_state.value.canSave) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = actions.save(memberId, look)) {
                is ApiResult.Success ->
                    _state.update {
                        it.copy(isBusy = false, saved = true, data = it.data?.copy(profile = result.data), owned = result.data.owned)
                    }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isBusy = false, error = result.error) }
                    if (result.error is AppError.Conflict) refresh(keepError = result.error)
                }
            }
        }
    }
}
