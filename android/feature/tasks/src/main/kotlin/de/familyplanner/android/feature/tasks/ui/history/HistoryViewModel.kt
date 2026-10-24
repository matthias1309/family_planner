package de.familyplanner.android.feature.tasks.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.tasks.domain.board.LoadHistoryUseCase
import de.familyplanner.android.feature.tasks.domain.board.TaskCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Verlauf des Kindes [memberId] mit Anzeigename [name]. Ein Koin-Parameter. */
data class HistoryArgs(
    val memberId: String,
    val name: String,
)

data class HistoryUiState(
    val name: String,
    val cards: List<TaskCard> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && cards.isEmpty()
}

class HistoryViewModel(
    private val args: HistoryArgs,
    private val loadHistory: LoadHistoryUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState(args.name))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            _state.update {
                when (val result = loadHistory(args.memberId)) {
                    // Der Server sortiert schon; die Reihenfolge hier festzuhalten schützt vor Änderungen am Vertrag.
                    is ApiResult.Success -> it.copy(isLoading = false, cards = result.data.sortedByDescending { card -> card.doneAt })
                    is ApiResult.Failure -> it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }
}
