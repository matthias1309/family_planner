package de.familyplanner.android.feature.meals.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.meals.domain.attendance.DeadlineSettingsOverview
import de.familyplanner.android.feature.meals.domain.attendance.LoadDeadlineSettingsUseCase
import de.familyplanner.android.feature.meals.domain.attendance.SaveDeadlineSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

private val DefaultOrderDay = DayOfWeek.SUNDAY
private val DefaultTime: LocalTime = LocalTime.of(16, 0)

data class DeadlineSettingsUiState(
    val overview: DeadlineSettingsOverview? = null,
    val orderDay: DayOfWeek = DefaultOrderDay,
    val orderTime: LocalTime = DefaultTime,
    val childTime: LocalTime = DefaultTime,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val error: AppError? = null,
    val saveError: AppError? = null,
)

sealed interface DeadlineSettingsEvent {
    data object Refresh : DeadlineSettingsEvent

    data class OrderDayChanged(
        val day: DayOfWeek,
    ) : DeadlineSettingsEvent

    data class OrderTimeChanged(
        val time: LocalTime,
    ) : DeadlineSettingsEvent

    data class ChildTimeChanged(
        val time: LocalTime,
    ) : DeadlineSettingsEvent

    data object Save : DeadlineSettingsEvent
}

class DeadlineSettingsViewModel(
    private val loadSettings: LoadDeadlineSettingsUseCase,
    private val saveSettings: SaveDeadlineSettingsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(DeadlineSettingsUiState())
    val state: StateFlow<DeadlineSettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: DeadlineSettingsEvent) {
        when (event) {
            DeadlineSettingsEvent.Refresh -> refresh()
            is DeadlineSettingsEvent.OrderDayChanged -> _state.update { it.copy(orderDay = event.day, saveError = null) }
            is DeadlineSettingsEvent.OrderTimeChanged -> _state.update { it.copy(orderTime = event.time, saveError = null) }
            is DeadlineSettingsEvent.ChildTimeChanged -> _state.update { it.copy(childTime = event.time, saveError = null) }
            DeadlineSettingsEvent.Save -> save()
        }
    }

    private fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = loadSettings()) {
                is ApiResult.Success -> _state.update { it.withOverview(result.data).copy(isLoading = false) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    private fun save() {
        val current = _state.value
        if (current.isBusy || current.overview == null) return
        _state.update { it.copy(isBusy = true, saveError = null) }
        viewModelScope.launch {
            when (val result = saveSettings(current.orderDay, current.orderTime, current.childTime)) {
                // Die Antwort enthält schon den vorgemerkten Stand — kein zweiter GET nötig.
                is ApiResult.Success -> _state.update { it.copy(overview = result.data, isBusy = false) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, saveError = result.error) }
            }
        }
    }
}

/** Das Formular startet mit dem vorgemerkten Stand, sonst mit dem aktuellen. */
private fun DeadlineSettingsUiState.withOverview(overview: DeadlineSettingsOverview): DeadlineSettingsUiState {
    val shown = overview.scheduled ?: overview.current
    return copy(overview = overview, orderDay = shown.orderDay, orderTime = shown.orderTime, childTime = shown.childTime)
}
