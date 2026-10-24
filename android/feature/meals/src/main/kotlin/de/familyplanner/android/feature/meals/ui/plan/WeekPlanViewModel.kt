package de.familyplanner.android.feature.meals.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceLimits
import de.familyplanner.android.feature.meals.domain.attendance.currentPlanWeek
import de.familyplanner.android.feature.meals.domain.attendance.currentWeek
import de.familyplanner.android.feature.meals.domain.cooking.CookOverview
import de.familyplanner.android.feature.meals.domain.cooking.CookingUseCases
import de.familyplanner.android.feature.meals.domain.cooking.StatusOverview
import de.familyplanner.android.feature.meals.domain.plan.LoadWeekPlanUseCase
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanLoad
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import de.familyplanner.android.feature.meals.domain.reminder.LoadPendingCookingUseCase
import de.familyplanner.android.feature.meals.domain.reminder.PendingCooking
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
    /** Montag der Planwoche; Status und Kochtag gibt es ab hier. */
    val planWeek: LocalDate = weekStart.plusWeeks(1),
    val memberId: String? = null,
    val status: StatusOverview? = null,
    val statusError: AppError? = null,
    /** Koch, für den der Absage-Dialog offen ist. */
    val cancelTarget: String? = null,
    val cancelReason: String = "",
    val isBusy: Boolean = false,
    val actionError: AppError? = null,
    /** Offene Einreichung laut lokalem Stand (wochenunabhängig, REQ-MEAL-051). */
    val pendingCooking: PendingCooking? = null,
) {
    /** Kind ohne Verbindung zum Heimnetz mit offener Einreichung: Hinweis auf den Weg per WhatsApp. */
    val showsWhatsAppHint: Boolean
        get() = !isParent && pendingCooking != null && (isOffline || error.isUnreachable() || statusError.isUnreachable())
    val canEdit: Boolean get() = isParent && !isOffline && week != null
    val showsStatus: Boolean get() = weekStart >= planWeek && !isOffline
    val myCooking: CookOverview? get() = if (isParent) null else status?.cooks?.firstOrNull { it.status.memberId == memberId }
    val showsPlanWeekLink: Boolean get() = weekStart == planWeek.minusWeeks(1)

    /** Eltern online öffnen jeden Tag; Kinder online nur ihr eigenes Gericht der Planwoche (REQ-MEAL-022). */
    fun canOpen(day: PlannedDay): Boolean =
        when {
            canEdit -> true
            isParent || isOffline -> false
            else -> showsStatus && day.dish?.cookId == memberId
        }

    /** Eltern dürfen für jedes Kind absagen/zurücknehmen, Kinder nur für sich selbst (der Server prüft zusätzlich). */
    fun mayAct(targetMemberId: String): Boolean = isParent || targetMemberId == memberId

    fun dishDate(dishId: String): LocalDate? = week?.days?.firstOrNull { it.dish?.id == dishId }?.date

    fun rejectionReasonOf(dishId: String?): String? =
        week
            ?.days
            ?.firstOrNull { dishId != null && it.dish?.id == dishId }
            ?.dish
            ?.rejectionReason

    val canGoPrevious: Boolean get() = weekStart.minusWeeks(1).year >= AttendanceLimits.MIN_YEAR
    val canGoNext: Boolean get() = weekStart.plusWeeks(1).year <= AttendanceLimits.MAX_YEAR
}

private fun AppError?.isUnreachable() = this == AppError.Network || this == AppError.Certificate

sealed interface WeekPlanEvent {
    data object Refresh : WeekPlanEvent

    data object PreviousWeek : WeekPlanEvent

    data object NextWeek : WeekPlanEvent

    data object GoToPlanWeek : WeekPlanEvent

    data class RequestCancel(
        val memberId: String,
    ) : WeekPlanEvent

    data class CancelReasonChanged(
        val value: String,
    ) : WeekPlanEvent

    data object DismissCancel : WeekPlanEvent

    data object ConfirmCancel : WeekPlanEvent

    data class Withdraw(
        val memberId: String,
    ) : WeekPlanEvent
}

class WeekPlanViewModel(
    sessions: SessionStore,
    private val loadWeekPlan: LoadWeekPlanUseCase,
    private val cooking: CookingUseCases,
    private val loadPendingCooking: LoadPendingCookingUseCase,
    private val clock: Clock = Clock.system(BerlinZone),
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            WeekPlanUiState(
                weekStart = currentWeek(clock.instant()),
                today = clock.instant().atZone(BerlinZone).toLocalDate(),
                isParent = sessions.current?.role == Role.PARENT,
                planWeek = currentPlanWeek(clock.instant()),
                memberId = sessions.current?.memberId,
            ),
        )
    val state: StateFlow<WeekPlanUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var statusJob: Job? = null

    init {
        load()
        prefetch(_state.value.weekStart.plusWeeks(1))
    }

    fun onEvent(event: WeekPlanEvent) {
        when (event) {
            WeekPlanEvent.Refresh -> load()
            WeekPlanEvent.PreviousWeek -> if (_state.value.canGoPrevious) switchWeek(-1)
            WeekPlanEvent.NextWeek -> if (_state.value.canGoNext) switchWeek(1)
            WeekPlanEvent.GoToPlanWeek -> switchWeek(_state.value.planWeek)
            else -> onCooking(event)
        }
    }

    private fun onCooking(event: WeekPlanEvent) {
        when (event) {
            is WeekPlanEvent.RequestCancel ->
                _state.update {
                    if (it.mayAct(event.memberId)) it.copy(cancelTarget = event.memberId, cancelReason = "", actionError = null) else it
                }
            is WeekPlanEvent.CancelReasonChanged -> _state.update { it.copy(cancelReason = event.value.take(PlanLimits.REASON_MAX)) }
            WeekPlanEvent.DismissCancel -> _state.update { it.copy(cancelTarget = null, actionError = null) }
            WeekPlanEvent.ConfirmCancel -> confirmCancel()
            is WeekPlanEvent.Withdraw -> withdraw(event.memberId)
            else -> Unit
        }
    }

    private fun confirmCancel() {
        val current = _state.value
        val target = current.cancelTarget ?: return
        if (current.isBusy || !current.mayAct(target)) return
        _state.update { it.copy(isBusy = true, actionError = null) }
        viewModelScope.launch {
            val result = cooking.cancel(current.weekStart, target, current.cancelReason)
            // Wurde inzwischen die Woche gewechselt, gehört das Ergebnis nicht mehr zur Anzeige.
            if (_state.value.weekStart != current.weekStart) return@launch _state.update { it.copy(isBusy = false) }
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isBusy = false, cancelTarget = null) }
                    load()
                }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, actionError = result.error) }
            }
        }
    }

    private fun withdraw(memberId: String) {
        val current = _state.value
        if (current.isBusy || !current.mayAct(memberId)) return
        _state.update { it.copy(isBusy = true, actionError = null) }
        viewModelScope.launch {
            val result = cooking.withdraw(current.weekStart, memberId)
            if (_state.value.weekStart != current.weekStart) return@launch _state.update { it.copy(isBusy = false) }
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isBusy = false) }
                    load()
                }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, actionError = result.error) }
            }
        }
    }

    private fun switchWeek(deltaWeeks: Long) = switchWeek(_state.value.weekStart.plusWeeks(deltaWeeks))

    private fun switchWeek(target: LocalDate) {
        // Alte Daten verwerfen, damit nie die Daten einer anderen Woche unter dem neuen Titel stehen.
        _state.update {
            it.copy(
                weekStart = target,
                week = null,
                isOffline = false,
                cacheReason = null,
                status = null,
                statusError = null,
                actionError = null,
                cancelTarget = null,
            )
        }
        load()
    }

    /** Lädt die Woche nur für den Cache (ADR-0011); das Ergebnis wird nicht angezeigt. Eigener Job, damit `loadJob` nicht abbricht. */
    private fun prefetch(week: LocalDate) {
        viewModelScope.launch { loadWeekPlan(week) }
    }

    private fun load() {
        loadJob?.cancel()
        val requested = _state.value.weekStart
        // „Heute“ und Planwoche bei jedem Laden neu bestimmen, falls die App über Mitternacht/Montag offen blieb.
        _state.update {
            it.copy(
                isLoading = true,
                error = null,
                today = clock.instant().atZone(BerlinZone).toLocalDate(),
                planWeek = currentPlanWeek(clock.instant()),
            )
        }
        loadJob =
            viewModelScope.launch {
                val result = loadWeekPlan(requested)
                // Eine späte Antwort der vorherigen Woche darf die aktuelle Anzeige nicht überschreiben.
                if (_state.value.weekStart != requested) return@launch
                _state.update { it.withResult(result) }
            }
        loadStatus(requested)
        loadPending()
    }

    private fun loadPending() {
        if (_state.value.isParent) return
        viewModelScope.launch { _state.update { it.copy(pendingCooking = loadPendingCooking()) } }
    }

    /** Wochenstatus nur ab der Planwoche, parallel zum Plan; späte Antworten einer anderen Woche werden verworfen. */
    private fun loadStatus(requested: LocalDate) {
        statusJob?.cancel()
        if (requested < _state.value.planWeek) {
            _state.update { it.copy(status = null, statusError = null) }
            return
        }
        statusJob =
            viewModelScope.launch {
                val result = cooking.status(requested)
                if (_state.value.weekStart != requested) return@launch
                when (result) {
                    is ApiResult.Success -> _state.update { it.copy(status = result.data, statusError = null) }
                    is ApiResult.Failure -> _state.update { it.copy(status = null, statusError = result.error) }
                }
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
