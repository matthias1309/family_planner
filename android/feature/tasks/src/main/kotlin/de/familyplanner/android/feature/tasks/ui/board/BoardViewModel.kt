package de.familyplanner.android.feature.tasks.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.map
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.tasks.domain.board.BoardLoad
import de.familyplanner.android.feature.tasks.domain.board.CardAction
import de.familyplanner.android.feature.tasks.domain.board.LoadBoardUseCase
import de.familyplanner.android.feature.tasks.domain.board.MoveCardUseCase
import de.familyplanner.android.feature.tasks.domain.board.RemoveCardUseCase
import de.familyplanner.android.feature.tasks.domain.board.TaskCard
import de.familyplanner.android.feature.tasks.domain.board.TaskLane
import de.familyplanner.android.feature.tasks.domain.board.TaskLimits
import de.familyplanner.android.feature.tasks.domain.board.cardActions
import de.familyplanner.android.feature.tasks.domain.board.orderedLanes
import de.familyplanner.android.feature.tasks.domain.board.removing
import de.familyplanner.android.feature.tasks.domain.board.replacing
import de.familyplanner.android.feature.tasks.domain.board.reviewCount
import de.familyplanner.android.feature.tasks.domain.board.reviewQueue
import de.familyplanner.android.feature.tasks.domain.board.sections
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Reiter des Boards: die Sammelansicht der Eltern oder die Spur eines Kindes. */
sealed interface BoardTab {
    data object Review : BoardTab

    data class Lane(
        val lane: TaskLane,
    ) : BoardTab
}

data class BoardUiState(
    val memberId: String,
    val isParent: Boolean,
    val lanes: List<TaskLane> = emptyList(),
    val selectedTab: Int = 0,
    val syncedAt: Instant? = null,
    val isOffline: Boolean = false,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val isBusy: Boolean = false,
    val actionError: AppError? = null,
    val rejecting: TaskCard? = null,
    val rejectReason: String = "",
    val confirming: Pair<TaskCard, CardAction>? = null,
) {
    private val ordered: List<TaskLane> get() = orderedLanes(lanes, memberId, isParent)
    val tabs: List<BoardTab>
        get() =
            when {
                lanes.isEmpty() -> emptyList()
                isParent -> listOf(BoardTab.Review) + ordered.map { BoardTab.Lane(it) }
                else -> ordered.map { BoardTab.Lane(it) }
            }
    val selectedIndex: Int get() = selectedTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val selectedTabContent: BoardTab? get() = tabs.getOrNull(selectedIndex)
    val reviewCount: Int get() = reviewCount(lanes)
    val reviewQueue: List<Pair<TaskLane, TaskCard>> get() = reviewQueue(lanes)

    fun actionsFor(card: TaskCard): List<CardAction> = cardActions(card, memberId, isParent, isOffline)

    fun sectionsOf(lane: TaskLane) = sections(lane.cards)
}

sealed interface BoardEvent {
    data object Refresh : BoardEvent

    data class SelectTab(
        val index: Int,
    ) : BoardEvent

    data class Act(
        val card: TaskCard,
        val action: CardAction,
    ) : BoardEvent

    data class RejectReasonChanged(
        val value: String,
    ) : BoardEvent

    data object ConfirmReject : BoardEvent

    data object ConfirmAction : BoardEvent

    data object DismissDialog : BoardEvent
}

class BoardViewModel(
    sessions: SessionStore,
    private val loadBoard: LoadBoardUseCase,
    private val moveCard: MoveCardUseCase,
    private val removeCard: RemoveCardUseCase,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            BoardUiState(
                memberId = sessions.current?.memberId.orEmpty(),
                isParent = sessions.current?.role == Role.PARENT,
            ),
        )
    val state: StateFlow<BoardUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: BoardEvent) {
        when (event) {
            BoardEvent.Refresh -> {
                _state.update { it.copy(actionError = null) }
                load()
            }
            is BoardEvent.SelectTab -> _state.update { it.copy(selectedTab = event.index) }
            is BoardEvent.Act -> act(event.card, event.action)
            is BoardEvent.RejectReasonChanged -> _state.update { it.copy(rejectReason = event.value.take(TaskLimits.REASON_MAX)) }
            BoardEvent.ConfirmReject -> _state.value.rejecting?.let { perform(it, CardAction.REJECT, _state.value.rejectReason) }
            BoardEvent.ConfirmAction -> _state.value.confirming?.let { (card, action) -> perform(card, action) }
            BoardEvent.DismissDialog -> _state.update { it.copy(rejecting = null, rejectReason = "", confirming = null) }
        }
    }

    private fun act(
        card: TaskCard,
        action: CardAction,
    ) {
        if (_state.value.isBusy) return
        when (action) {
            CardAction.REJECT -> _state.update { it.copy(rejecting = card, rejectReason = "", actionError = null) }
            CardAction.ACCEPT_DIRECTLY, CardAction.REMOVE -> _state.update { it.copy(confirming = card to action, actionError = null) }
            else -> perform(card, action)
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                when (val result = loadBoard()) {
                    is BoardLoad.Fresh -> show(result.board.lanes, result.board.syncedAt, offline = false)
                    is BoardLoad.Cached -> show(result.board.lanes, result.board.syncedAt, offline = true)
                    is BoardLoad.Failed -> _state.update { it.copy(isLoading = false, error = result.error) }
                }
            }
    }

    private fun show(
        lanes: List<TaskLane>,
        syncedAt: Instant,
        offline: Boolean,
    ) {
        _state.update { it.copy(isLoading = false, error = null, lanes = lanes, syncedAt = syncedAt, isOffline = offline) }
        // Ein Reiter kann verschwunden sein (Kind entfernt): Auswahl auf einen gültigen Bereich klemmen.
        _state.update { it.copy(selectedTab = it.selectedIndex) }
    }

    private fun perform(
        card: TaskCard,
        action: CardAction,
        reason: String? = null,
    ) {
        if (_state.value.isBusy) return
        // Ein laufendes Neuladen könnte das frischere Ergebnis dieser Aktion sonst überschreiben.
        loadJob?.cancel()
        _state.update {
            it.copy(
                isBusy = true,
                isLoading = false,
                actionError = null,
                rejecting = null,
                rejectReason = "",
                confirming = null,
            )
        }
        viewModelScope.launch {
            when (val result = change(card, action, reason)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, lanes = result.data(it.lanes)) }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isBusy = false, actionError = result.error) }
                    // Jemand war schneller (409) oder die Karte ist weg (404): aktuellen Stand holen.
                    if (result.error is AppError.Conflict || result.error == AppError.NotFound) load()
                }
            }
        }
    }

    private suspend fun change(
        card: TaskCard,
        action: CardAction,
        reason: String?,
    ): ApiResult<(List<TaskLane>) -> List<TaskLane>> =
        if (action == CardAction.REMOVE) {
            removeCard(card.id).map { { lanes: List<TaskLane> -> lanes.removing(card.id) } }
        } else {
            moveCard(card.id, action, reason).map { updated -> { lanes: List<TaskLane> -> lanes.replacing(updated) } }
        }
}
