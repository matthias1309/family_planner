package de.familyplanner.android.feature.meals.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceLimits
import de.familyplanner.android.feature.meals.domain.attendance.LoadWeekOverviewUseCase
import de.familyplanner.android.feature.meals.domain.attendance.WeekOverview
import de.familyplanner.android.feature.meals.domain.attendance.currentPlanWeek
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class WeekOverviewUiState(
    val weekStart: LocalDate,
    val overview: WeekOverview? = null,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val isParent: Boolean = false,
) {
    val canGoPrevious: Boolean get() = weekStart.minusWeeks(1).year >= AttendanceLimits.MIN_YEAR
    val canGoNext: Boolean get() = weekStart.plusWeeks(1).year <= AttendanceLimits.MAX_YEAR
}

sealed interface WeekOverviewEvent {
    data object Refresh : WeekOverviewEvent

    data object PreviousWeek : WeekOverviewEvent

    data object NextWeek : WeekOverviewEvent
}

class WeekOverviewViewModel(
    sessions: SessionStore,
    private val loadWeek: LoadWeekOverviewUseCase,
    clock: Clock = Clock.system(BerlinZone),
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            WeekOverviewUiState(
                weekStart = currentPlanWeek(clock.instant()),
                isParent = sessions.current?.role == Role.PARENT,
            ),
        )
    val state: StateFlow<WeekOverviewUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: WeekOverviewEvent) {
        when (event) {
            WeekOverviewEvent.Refresh -> load()
            WeekOverviewEvent.PreviousWeek -> if (_state.value.canGoPrevious) switchWeek(-1)
            WeekOverviewEvent.NextWeek -> if (_state.value.canGoNext) switchWeek(1)
        }
    }

    private fun switchWeek(deltaWeeks: Long) {
        // Alte Daten verwerfen, damit nie die Daten einer anderen Woche unter dem neuen Titel stehen.
        _state.update { it.copy(weekStart = it.weekStart.plusWeeks(deltaWeeks), overview = null) }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        val requested = _state.value.weekStart
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                val result = loadWeek(requested)
                // Eine späte Antwort der vorherigen Woche darf die aktuelle Anzeige nicht überschreiben.
                if (_state.value.weekStart != requested) return@launch
                when (result) {
                    is ApiResult.Success -> _state.update { it.copy(isLoading = false, overview = result.data) }
                    is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
                }
            }
    }
}
