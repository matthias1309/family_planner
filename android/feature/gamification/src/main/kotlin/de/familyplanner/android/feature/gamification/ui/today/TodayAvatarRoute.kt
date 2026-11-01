package de.familyplanner.android.feature.gamification.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.avatar.AvatarLook
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarDirectory
import de.familyplanner.android.feature.gamification.ui.avatar.MyAvatarCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class TodayAvatarUiState(
    /** `false` bei Eltern: dort wird nichts angezeigt. */
    val isChild: Boolean = false,
    val name: String = "",
    val look: AvatarLook? = null,
)

/** Lädt im `init` (Heute hat kein ON_RESUME-Muster); Eltern sehen keine Karte. */
class TodayAvatarViewModel(
    sessions: SessionStore,
    avatars: AvatarDirectory,
) : ViewModel() {
    private val session = sessions.current
    private val _state = MutableStateFlow(TodayAvatarUiState(isChild = session?.role == Role.CHILD, name = session?.memberName.orEmpty()))
    val state: StateFlow<TodayAvatarUiState> = _state.asStateFlow()

    init {
        val memberId = session?.memberId
        if (_state.value.isChild && memberId != null) {
            viewModelScope.launch { avatars.ensureLoaded() }
            viewModelScope.launch { avatars.looks.collect { looks -> _state.update { it.copy(look = looks[memberId]) } } }
        }
    }
}

@Composable
fun TodayAvatarRoute(onOpenAvatar: () -> Unit) {
    val viewModel = koinViewModel<TodayAvatarViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayAvatarCard(state, onOpenAvatar)
}

@Composable
fun TodayAvatarCard(
    state: TodayAvatarUiState,
    onOpenAvatar: () -> Unit,
) {
    if (!state.isChild) return
    MyAvatarCard(state.look, state.name, enabled = true, onClick = onOpenAvatar)
}
