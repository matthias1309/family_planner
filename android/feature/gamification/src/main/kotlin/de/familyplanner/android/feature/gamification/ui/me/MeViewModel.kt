package de.familyplanner.android.feature.gamification.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.gamification.domain.LoadPointsUseCase
import de.familyplanner.android.feature.gamification.domain.PointsLoad
import de.familyplanner.android.feature.gamification.domain.PointsSnapshot
import de.familyplanner.android.feature.gamification.domain.RankedChild
import de.familyplanner.android.feature.gamification.domain.rankOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeUiState(
    val memberId: String? = null,
    val snapshot: PointsSnapshot? = null,
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val error: AppError? = null,
) {
    val myRank: RankedChild? get() = memberId?.let { snapshot?.leaderboard?.rankOf(it) }
}

sealed interface MeEvent {
    data object Refresh : MeEvent
}

/** Geladen wird nur über [MeEvent.Refresh] (erstes `ON_RESUME`), nicht im `init`. */
class MeViewModel(
    private val loadPoints: LoadPointsUseCase,
    sessions: SessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(MeUiState(memberId = sessions.current?.memberId))
    val state: StateFlow<MeUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun onEvent(event: MeEvent) {
        when (event) {
            MeEvent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                val result = loadPoints()
                _state.update {
                    when (result) {
                        is PointsLoad.Fresh -> it.copy(isLoading = false, snapshot = result.snapshot, isOffline = false)
                        is PointsLoad.Cached -> it.copy(isLoading = false, snapshot = result.snapshot, isOffline = true)
                        is PointsLoad.Failed -> it.copy(isLoading = false, error = result.error)
                    }
                }
            }
    }
}
