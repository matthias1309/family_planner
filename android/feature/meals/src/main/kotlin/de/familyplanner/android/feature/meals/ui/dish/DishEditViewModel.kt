package de.familyplanner.android.feature.meals.ui.dish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.meals.domain.attendance.mondayOf
import de.familyplanner.android.feature.meals.domain.plan.DishEditorData
import de.familyplanner.android.feature.meals.domain.plan.DishUseCases
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.domain.plan.PlanMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DishEditUiState(
    val dishId: String?,
    val weekStart: LocalDate,
    val date: LocalDate,
    val name: String = "",
    val ingredientsText: String = "",
    val cookId: String? = null,
    val parents: List<PlanMember> = emptyList(),
    /** Tage dieser Woche mit einem anderen Gericht — nicht wählbar (Server: 409). */
    val takenDates: Set<LocalDate> = emptySet(),
    val isLoading: Boolean = true,
    val loadError: AppError? = null,
    val isBusy: Boolean = false,
    val error: AppError? = null,
    val showDeleteConfirm: Boolean = false,
    /** Gespeichert oder gelöscht → die Route geht zurück. */
    val isDone: Boolean = false,
) {
    val isNew: Boolean get() = dishId == null
    val canSave: Boolean get() = !isLoading && loadError == null && cookId != null && name.isNotBlank() && date !in takenDates && !isBusy
}

sealed interface DishEditEvent {
    data class NameChanged(
        val value: String,
    ) : DishEditEvent

    data class IngredientsChanged(
        val value: String,
    ) : DishEditEvent

    data class DateSelected(
        val date: LocalDate,
    ) : DishEditEvent

    data class CookSelected(
        val memberId: String,
    ) : DishEditEvent

    data object Save : DishEditEvent

    data object RequestDelete : DishEditEvent

    data object CancelDelete : DishEditEvent

    data object ConfirmDelete : DishEditEvent

    data object Retry : DishEditEvent
}

class DishEditViewModel(
    date: LocalDate,
    private val dishId: String?,
    private val sessions: SessionStore,
    private val dishes: DishUseCases,
) : ViewModel() {
    private val _state = MutableStateFlow(DishEditUiState(dishId = dishId, weekStart = mondayOf(date), date = date))
    val state: StateFlow<DishEditUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: DishEditEvent) {
        when (event) {
            is DishEditEvent.NameChanged -> edit { it.copy(name = event.value.take(PlanLimits.NAME_MAX)) }
            is DishEditEvent.IngredientsChanged -> edit { it.copy(ingredientsText = event.value) }
            is DishEditEvent.DateSelected -> edit { if (event.date in it.takenDates) it else it.copy(date = event.date) }
            is DishEditEvent.CookSelected -> edit { it.copy(cookId = event.memberId) }
            DishEditEvent.Save -> save()
            DishEditEvent.RequestDelete -> _state.update { it.copy(showDeleteConfirm = true) }
            DishEditEvent.CancelDelete -> _state.update { it.copy(showDeleteConfirm = false) }
            DishEditEvent.ConfirmDelete -> delete()
            DishEditEvent.Retry -> load()
        }
    }

    private fun edit(change: (DishEditUiState) -> DishEditUiState) = _state.update { change(it).copy(error = null) }

    private fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = dishes.load(_state.value.weekStart)) {
                is ApiResult.Success -> _state.update { it.withLoaded(result.data) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    private fun DishEditUiState.withLoaded(data: DishEditorData): DishEditUiState {
        val planned = data.week.days.mapNotNull { it.dish }
        val taken = planned.filter { it.id != dishId }.map { it.date }.toSet()
        val parentIds = data.parents.map { it.id }
        val base = copy(isLoading = false, parents = data.parents, takenDates = taken)
        if (dishId == null) {
            val own = sessions.current?.memberId?.takeIf { it in parentIds }
            return base.copy(cookId = own ?: parentIds.firstOrNull())
        }
        val existing = planned.firstOrNull { it.id == dishId } ?: return base.copy(loadError = AppError.NotFound)
        return base.copy(
            date = existing.date,
            name = existing.name,
            ingredientsText = existing.ingredients.joinToString("\n"),
            cookId = existing.cookId.takeIf { it in parentIds },
        )
    }

    private fun save() {
        val current = _state.value
        val cookId = current.cookId
        if (!current.canSave || cookId == null) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = dishes.save(dishId, current.date, cookId, current.name, current.ingredientsText)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDone = true) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error) }
            }
        }
    }

    private fun delete() {
        val id = dishId ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, showDeleteConfirm = false, error = null) }
        viewModelScope.launch {
            when (val result = dishes.delete(id)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDone = true) }
                // Schon gelöscht (anderes Gerät): Ziel erreicht, der Plan lädt neu.
                is ApiResult.Failure ->
                    if (result.error == AppError.NotFound) {
                        _state.update { it.copy(isBusy = false, isDone = true) }
                    } else {
                        _state.update { it.copy(isBusy = false, error = result.error) }
                    }
            }
        }
    }
}
