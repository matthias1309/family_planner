package de.familyplanner.android.feature.gamification.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.feature.gamification.domain.LoadTodaySavingsUseCase
import de.familyplanner.android.feature.gamification.domain.SavingsGoal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodaySavingsUiState(
    val goal: SavingsGoal? = null,
)

/** Lädt im `init` (Heute hat kein ON_RESUME-Muster). */
class TodaySavingsViewModel(
    private val load: LoadTodaySavingsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(TodaySavingsUiState())
    val state: StateFlow<TodaySavingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load()?.let { goal -> _state.update { it.copy(goal = goal) } } }
    }
}
