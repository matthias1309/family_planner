package de.familyplanner.android.feature.gamification.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.gamification.domain.CookingPointsUseCase
import de.familyplanner.android.feature.gamification.domain.LoadPointsUseCase
import de.familyplanner.android.feature.gamification.domain.PointsLimits
import de.familyplanner.android.feature.gamification.domain.PointsLoad
import de.familyplanner.android.feature.gamification.domain.PointsSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FamilyPointsUiState(
    val snapshot: PointsSnapshot? = null,
    val cookingPoints: Int? = null,
    val isOffline: Boolean = false,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    /** Offener Dialog „Kochpunkte“ mit dem gerade eingestellten Wert. */
    val editingCookingPoints: Int? = null,
    val isBusy: Boolean = false,
    val actionError: AppError? = null,
)

sealed interface FamilyPointsEvent {
    data object Refresh : FamilyPointsEvent

    data object EditCookingPoints : FamilyPointsEvent

    data class CookingPointsChanged(
        val points: Int,
    ) : FamilyPointsEvent

    data object SaveCookingPoints : FamilyPointsEvent

    data object DismissDialog : FamilyPointsEvent
}

class FamilyPointsViewModel(
    private val loadPoints: LoadPointsUseCase,
    private val cookingPointsUseCase: CookingPointsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(FamilyPointsUiState())
    val state: StateFlow<FamilyPointsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun onEvent(event: FamilyPointsEvent) {
        when (event) {
            FamilyPointsEvent.Refresh -> refresh()
            FamilyPointsEvent.EditCookingPoints ->
                _state.update { s ->
                    s.cookingPoints?.takeIf { !s.isOffline }?.let { s.copy(editingCookingPoints = it, actionError = null) } ?: s
                }
            is FamilyPointsEvent.CookingPointsChanged ->
                _state.update {
                    it.copy(editingCookingPoints = event.points.coerceIn(PointsLimits.COOKING_POINTS_MIN, PointsLimits.COOKING_POINTS_MAX))
                }
            FamilyPointsEvent.SaveCookingPoints -> save()
            FamilyPointsEvent.DismissDialog -> _state.update { it.copy(editingCookingPoints = null, actionError = null) }
        }
    }

    private fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                val (points, settings) =
                    coroutineScope {
                        val p = async { loadPoints() }
                        val s = async { cookingPointsUseCase.load() }
                        p.await() to s.await()
                    }
                val cooking = (settings as? ApiResult.Success)?.data
                _state.update {
                    when (points) {
                        is PointsLoad.Fresh ->
                            it.copy(
                                isLoading = false,
                                snapshot = points.snapshot,
                                isOffline = false,
                                cookingPoints = cooking ?: it.cookingPoints,
                            )
                        is PointsLoad.Cached ->
                            it.copy(
                                isLoading = false,
                                snapshot = points.snapshot,
                                isOffline = true,
                                cookingPoints = cooking ?: it.cookingPoints,
                            )
                        is PointsLoad.Failed ->
                            it.copy(
                                isLoading = false,
                                error = points.error,
                                cookingPoints = cooking ?: it.cookingPoints,
                            )
                    }
                }
            }
    }

    private fun save() {
        val current = _state.value
        val points = current.editingCookingPoints ?: return
        if (current.isBusy || current.isOffline) return
        _state.update { it.copy(isBusy = true, actionError = null) }
        viewModelScope.launch {
            when (val result = cookingPointsUseCase.save(points)) {
                is ApiResult.Success ->
                    _state.update { it.copy(isBusy = false, cookingPoints = result.data, editingCookingPoints = null) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, actionError = result.error) }
            }
        }
    }
}
