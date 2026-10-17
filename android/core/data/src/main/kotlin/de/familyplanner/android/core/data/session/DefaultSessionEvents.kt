package de.familyplanner.android.core.data.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DefaultSessionEvents : SessionEvents {
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = EVENT_BUFFER)

    override val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    override fun emit(event: SessionEvent) {
        _events.tryEmit(event)
    }

    private companion object {
        const val EVENT_BUFFER = 4
    }
}
