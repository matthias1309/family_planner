package de.familyplanner.android.core.testing

import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.data.session.SessionState
import de.familyplanner.android.core.data.session.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

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

    /** Simuliert einen Speicherfehler (z. B. Dateisystem voll). */
    var saveFailure: IOException? = null

    override suspend fun save(session: Session) {
        saveFailure?.let { throw it }
        setCurrent(session)
    }

    override suspend fun clear() = setCurrent(null)
}
