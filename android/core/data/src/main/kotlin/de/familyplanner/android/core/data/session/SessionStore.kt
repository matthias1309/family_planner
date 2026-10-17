package de.familyplanner.android.core.data.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    data class SignedIn(
        val session: Session,
    ) : SessionState
}

interface SessionStore {
    /** `Loading`, solange die Session noch vom Speicher gelesen wird. */
    val state: Flow<SessionState>

    /** Synchroner Zugriff für Interceptoren (In-Memory-Cache). */
    val current: Session?

    suspend fun save(session: Session)

    suspend fun clear()
}

sealed interface SessionEvent {
    /** Server hat das Token abgelehnt (z. B. Gerät entfernt). */
    data object SignedOut : SessionEvent
}

interface SessionEvents {
    val events: SharedFlow<SessionEvent>

    fun emit(event: SessionEvent)
}
