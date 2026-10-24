package de.familyplanner.android.feature.meals.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceLimits
import de.familyplanner.android.feature.meals.domain.attendance.currentPlanWeek
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingList
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingSections
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingUseCases
import de.familyplanner.android.feature.meals.domain.shopping.splitByChecked
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** [weekStart] `null` = Planwoche (Montag nach heute, Berlin). Ein Koin-Parameter. */
data class ShoppingListArgs(
    val weekStart: LocalDate? = null,
)

data class ShoppingListUiState(
    val weekStart: LocalDate,
    val list: ShoppingList? = null,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val isParent: Boolean = false,
    val newItem: String = "",
    val isBusy: Boolean = false,
    val actionError: AppError? = null,
) {
    val sections: ShoppingSections get() = splitByChecked(list?.entries.orEmpty())
    val canEdit: Boolean get() = isParent && list != null
    val freeItemCount: Int get() = list?.entries?.count { it.isFreeItem } ?: 0
    val isEmpty: Boolean get() = list != null && list.entries.isEmpty()
    val canGoPrevious: Boolean get() = weekStart.minusWeeks(1).year >= AttendanceLimits.MIN_YEAR
    val canGoNext: Boolean get() = weekStart.plusWeeks(1).year <= AttendanceLimits.MAX_YEAR
}

sealed interface ShoppingListEvent {
    data object Refresh : ShoppingListEvent

    data object PreviousWeek : ShoppingListEvent

    data object NextWeek : ShoppingListEvent

    data class NewItemChanged(
        val value: String,
    ) : ShoppingListEvent

    data object AddItem : ShoppingListEvent

    data class SetChecked(
        val key: String,
        val checked: Boolean,
    ) : ShoppingListEvent

    data class DeleteItem(
        val itemId: String,
    ) : ShoppingListEvent
}

class ShoppingListViewModel(
    args: ShoppingListArgs,
    sessions: SessionStore,
    private val shopping: ShoppingUseCases,
    clock: Clock = Clock.system(BerlinZone),
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            ShoppingListUiState(
                weekStart = args.weekStart ?: currentPlanWeek(clock.instant()),
                isParent = sessions.current?.role == Role.PARENT,
            ),
        )
    val state: StateFlow<ShoppingListUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: ShoppingListEvent) {
        when (event) {
            ShoppingListEvent.Refresh -> {
                _state.update { it.copy(actionError = null) }
                load()
            }
            ShoppingListEvent.PreviousWeek -> if (_state.value.canGoPrevious) switchWeek(-1)
            ShoppingListEvent.NextWeek -> if (_state.value.canGoNext) switchWeek(1)
            is ShoppingListEvent.NewItemChanged -> _state.update { it.copy(newItem = event.value.take(PlanLimits.INGREDIENT_MAX)) }
            ShoppingListEvent.AddItem -> addItem()
            is ShoppingListEvent.SetChecked -> setChecked(event.key, event.checked)
            is ShoppingListEvent.DeleteItem -> deleteItem(event.itemId)
        }
    }

    private fun switchWeek(offset: Long) {
        _state.update {
            it.copy(
                weekStart = it.weekStart.plusWeeks(offset),
                list = null,
                error = null,
                actionError = null,
                newItem = "",
            )
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        val requested = _state.value.weekStart
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                val result = shopping.load(requested)
                // Eine späte Antwort der vorherigen Woche darf die aktuelle Anzeige nicht überschreiben.
                if (_state.value.weekStart != requested) return@launch
                _state.update {
                    when (result) {
                        is ApiResult.Success -> it.copy(isLoading = false, list = result.data)
                        is ApiResult.Failure -> it.copy(isLoading = false, list = null, error = result.error)
                    }
                }
            }
    }

    private fun addItem() {
        val current = _state.value
        write(current, clearsInput = true) { week -> shopping.add(week, current.newItem, current.freeItemCount) }
    }

    private fun setChecked(
        key: String,
        checked: Boolean,
    ) {
        write(_state.value, reloadsOnNotFound = true) { week -> shopping.check(week, key, checked) }
    }

    private fun deleteItem(itemId: String) {
        val current = _state.value
        if (!current.canEdit || current.isBusy) return
        // Ein laufendes Neuladen könnte das frischere Ergebnis dieses Schreibvorgangs sonst überschreiben.
        loadJob?.cancel()
        _state.update { it.copy(isBusy = true, isLoading = false, actionError = null) }
        viewModelScope.launch {
            val result = shopping.delete(current.weekStart, itemId)
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

    /** Ein Schreibvorgang, der die ganze Liste zurückgibt (Hinzufügen, Abhaken). */
    private fun write(
        current: ShoppingListUiState,
        clearsInput: Boolean = false,
        reloadsOnNotFound: Boolean = false,
        request: suspend (LocalDate) -> ApiResult<ShoppingList>,
    ) {
        if (!current.canEdit || current.isBusy) return
        // Ein laufendes Neuladen könnte das frischere Ergebnis dieses Schreibvorgangs sonst überschreiben.
        loadJob?.cancel()
        _state.update { it.copy(isBusy = true, isLoading = false, actionError = null) }
        viewModelScope.launch {
            val result = request(current.weekStart)
            if (_state.value.weekStart != current.weekStart) return@launch _state.update { it.copy(isBusy = false) }
            when (result) {
                is ApiResult.Success ->
                    _state.update {
                        it.copy(isBusy = false, list = result.data, newItem = if (clearsInput) "" else it.newItem)
                    }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isBusy = false, actionError = result.error) }
                    if (reloadsOnNotFound && result.error == AppError.NotFound) load()
                }
            }
        }
    }
}
