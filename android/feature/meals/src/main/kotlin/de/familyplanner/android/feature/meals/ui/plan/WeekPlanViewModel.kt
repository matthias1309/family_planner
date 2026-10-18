package de.familyplanner.android.feature.meals.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceLimits
import de.familyplanner.android.feature.meals.domain.attendance.currentWeek
import de.familyplanner.android.feature.meals.domain.plan.LoadWeekPlanUseCase
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanLoad
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class WeekPlanUiState(
    val weekStart: LocalDate,
    val today: LocalDate,
    val week: WeekPlanSnapshot? = null,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    /** Gezeigt wird ein gespeicherter Stand (REQ-MEAL-002). */
    val isOffline: Boolean = false,
    /** Grund für den gespeicherten Stand; `Server` heißt: erreichbar, aber gestört. */
    val cacheReason: AppError? = null,
    val isParent: Boolean = false,
) {
    val canEdit: Boolean get() = isParent && !isOffline && week != null
    val canGoPrevious: Boolean get() = weekStart.minusWeeks(1).year >= AttendanceLimits.MIN_YEAR
    val canGoNext: Boolean get() = weekStart.plusWeeks(1).year <= AttendanceLimits.MAX_YEAR
}

sealed interface WeekPlanEvent {
    data object Refresh : WeekPlanEvent

    data object PreviousWeek : WeekPlanEvent

    data object NextWeek : WeekPlanEvent
}

class WeekPlanViewModel(
    sessions: SessionStore,
    private val loadWeekPlan: LoadWeekPlanUseCase,
    private val clock: Clock = Clock.system(BerlinZone),
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            WeekPlanUiState(
                weekStart = currentWeek(clock.instant()),
                today = clock.instant().atZone(BerlinZone).toLocalDate(),
                isParent = sessions.current?.role == Role.PARENT,
            ),
        )
    val state: StateFlow<WeekPlanUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
        prefetch(_state.value.weekStart.plusWeeks(1))
    }

    fun onEvent(event: WeekPlanEvent) {
        when (event) {
            WeekPlanEvent.Refresh -> load()
            WeekPlanEvent.PreviousWeek -> if (_state.value.canGoPrevious) switchWeek(-1)
            WeekPlanEvent.NextWeek -> if (_state.value.canGoNext) switchWeek(1)
        }
    }

    private fun switchWeek(deltaWeeks: Long) {
        // Alte Daten verwerfen, damit nie die Daten einer anderen Woche unter dem neuen Titel stehen.
        _state.update { it.copy(weekStart = it.weekStart.plusWeeks(deltaWeeks), week = null, isOffline = false, cacheReason = null) }
        load()
    }

    /** Lädt die Woche nur für den Cache (ADR-0011); das Ergebnis wird nicht angezeigt. Eigener Job, damit `loadJob` nicht abbricht. */
    private fun prefetch(week: LocalDate) {
        viewModelScope.launch { loadWeekPlan(week) }
    }

    private fun load() {
        loadJob?.cancel()
        val requested = _state.value.weekStart
        // „Heute“ bei jedem Laden neu bestimmen, falls die App über Mitternacht offen blieb.
        _state.update { it.copy(isLoading = true, error = null, today = clock.instant().atZone(BerlinZone).toLocalDate()) }
        loadJob =
            viewModelScope.launch {
                val result = loadWeekPlan(requested)
                // Eine späte Antwort der vorherigen Woche darf die aktuelle Anzeige nicht überschreiben.
                if (_state.value.weekStart != requested) return@launch
                _state.update { it.withResult(result) }
            }
    }

    private fun WeekPlanUiState.withResult(result: WeekPlanLoad): WeekPlanUiState =
        when (result) {
            is WeekPlanLoad.Fresh -> copy(isLoading = false, week = result.week, isOffline = false, cacheReason = null, error = null)
            is WeekPlanLoad.Cached ->
                copy(
                    isLoading = false,
                    week = result.week,
                    isOffline = true,
                    cacheReason = result.error,
                    error = null,
                )
            is WeekPlanLoad.Failed -> copy(isLoading = false, week = null, isOffline = false, cacheReason = null, error = result.error)
        }
}
