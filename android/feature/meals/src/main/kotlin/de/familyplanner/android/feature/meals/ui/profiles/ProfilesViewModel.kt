package de.familyplanner.android.feature.meals.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.meals.domain.attendance.ChildProfile
import de.familyplanner.android.feature.meals.domain.attendance.LoadProfilesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<ChildProfile> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
)

sealed interface ProfilesEvent {
    data object Refresh : ProfilesEvent
}

class ProfilesViewModel(
    private val loadProfiles: LoadProfilesUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: ProfilesEvent) {
        when (event) {
            ProfilesEvent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = loadProfiles()) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, profiles = result.data) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
