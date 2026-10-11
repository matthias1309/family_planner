package de.familyplanner.android.core.testing

import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.data.session.SessionState
import de.familyplanner.android.core.data.session.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemorySessionStore(
    initial: Session? = null,
) : SessionStore {
    private val _state =
        MutableStateFlow<SessionState>(
            if (initial == null) SessionState.SignedOut else SessionState.SignedIn(initial),
        )

    override val state: Flow<SessionState> = _state

    override val current: Session?
        get() = (_state.value as? SessionState.SignedIn)?.session

    fun setCurrent(session: Session?) {
        _state.value = if (session == null) SessionState.SignedOut else SessionState.SignedIn(session)
    }

    override suspend fun save(session: Session) = setCurrent(session)

    override suspend fun clear() = setCurrent(null)
}
