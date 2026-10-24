package de.familyplanner.android.feature.tasks.ui

import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionState
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.tasks.domain.board.BoardCache
import de.familyplanner.android.feature.tasks.domain.board.reviewCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Zahl der Karten „Zur Abnahme“ aus dem lokalen Stand; nur für Eltern (REQ-TASK-003). */
class ReviewBadge(
    cache: BoardCache,
    sessions: SessionStore,
) {
    val count: Flow<Int> =
        combine(sessions.state, cache.observe()) { state, cached ->
            val session = (state as? SessionState.SignedIn)?.session
            if (session?.role == Role.PARENT && cached?.memberId == session.memberId) reviewCount(cached.board.lanes) else 0
        }
}
