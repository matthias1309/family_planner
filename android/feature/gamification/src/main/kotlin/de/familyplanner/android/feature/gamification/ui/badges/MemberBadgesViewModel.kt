package de.familyplanner.android.feature.gamification.ui.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.gamification.domain.BadgeOverview
import de.familyplanner.android.feature.gamification.domain.LoadBadgesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Badges des Mitglieds [memberId] mit Anzeigename [name]. Ein Koin-Parameter. */
data class MemberBadgesArgs(
    val memberId: String,
    val name: String,
)

data class MemberBadgesUiState(
    val args: MemberBadgesArgs,
    val overview: BadgeOverview? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
)

sealed interface MemberBadgesEvent {
    data object Refresh : MemberBadgesEvent
}

class MemberBadgesViewModel(
    private val args: MemberBadgesArgs,
    private val loadBadges: LoadBadgesUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(MemberBadgesUiState(args))
    val state: StateFlow<MemberBadgesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun onEvent(event: MemberBadgesEvent) {
        when (event) {
            MemberBadgesEvent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                val result = loadBadges(args.memberId)
                _state.update {
                    when (result) {
                        is ApiResult.Success -> it.copy(isLoading = false, overview = result.data)
                        is ApiResult.Failure -> it.copy(isLoading = false, error = result.error)
                    }
                }
            }
    }
}
