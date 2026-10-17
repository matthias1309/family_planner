package de.familyplanner.android.feature.auth.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.data.session.SessionState
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.auth.domain.RefreshMeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val session: Session? = null,
    val isOffline: Boolean = false,
) {
    val canManageFamily: Boolean get() = session?.role == Role.PARENT
}

class HomeViewModel(
    sessions: SessionStore,
    private val refreshMe: RefreshMeUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessions.state.collect { sessionState ->
                _state.update { it.copy(session = (sessionState as? SessionState.SignedIn)?.session) }
            }
        }
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val result = refreshMe()
            // Unauthorized räumt der UnauthorizedInterceptor auf; hier zählt nur „Server nicht erreichbar“.
            val offline = result is ApiResult.Failure && result.error.isConnectivityProblem()
            _state.update { it.copy(isOffline = offline) }
        }
    }

    private fun AppError.isConnectivityProblem(): Boolean = this == AppError.Network || this == AppError.Certificate
}
