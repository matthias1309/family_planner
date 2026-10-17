package de.familyplanner.android.core.data.session

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

private const val TAG = "SessionStore"

class DataStoreSessionStore(
    private val dataStore: DataStore<StoredSession>,
    scope: CoroutineScope,
) : SessionStore {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)

    override val state: Flow<SessionState> = _state

    override val current: Session?
        get() = (_state.value as? SessionState.SignedIn)?.session

    init {
        scope.launch {
            val stored =
                try {
                    dataStore.data.first().session
                } catch (e: IOException) {
                    // Unlesbarer Speicher: als abgemeldet behandeln statt ewig zu laden.
                    Log.w(TAG, "Session storage unreadable, treating as signed out", e)
                    null
                }
            // save()/clear() können schneller sein als der erste Lesevorgang.
            _state.compareAndSet(SessionState.Loading, stored.toState())
        }
    }

    override suspend fun save(session: Session) {
        dataStore.updateData { StoredSession(session) }
        _state.value = SessionState.SignedIn(session)
    }

    override suspend fun clear() {
        dataStore.updateData { StoredSession() }
        _state.value = SessionState.SignedOut
    }

    private fun Session?.toState(): SessionState = if (this == null) SessionState.SignedOut else SessionState.SignedIn(this)
}
