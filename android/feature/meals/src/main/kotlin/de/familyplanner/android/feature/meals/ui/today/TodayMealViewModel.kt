package de.familyplanner.android.feature.meals.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.meals.domain.attendance.mondayOf
import de.familyplanner.android.feature.meals.domain.plan.LoadWeekPlanUseCase
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** Gericht des heutigen Tages für „Heute“ (REQ-TASK-002); offline aus dem Plan-Cache (ADR-0011). */
data class TodayMealUiState(
    val isLoading: Boolean = true,
    val day: PlannedDay? = null,
    val unavailable: Boolean = false,
)

class TodayMealViewModel(
    private val loadWeekPlan: LoadWeekPlanUseCase,
    private val clock: Clock = Clock.system(BerlinZone),
) : ViewModel() {
    private val _state = MutableStateFlow(TodayMealUiState())
    val state: StateFlow<TodayMealUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            val snapshot =
                when (val result = loadWeekPlan(mondayOf(today))) {
                    is WeekPlanLoad.Fresh -> result.week
                    is WeekPlanLoad.Cached -> result.week
                    is WeekPlanLoad.Failed -> null
                }
            _state.value =
                TodayMealUiState(
                    isLoading = false,
                    day = snapshot?.days?.firstOrNull { it.date == today },
                    unavailable = snapshot == null,
                )
        }
    }
}
