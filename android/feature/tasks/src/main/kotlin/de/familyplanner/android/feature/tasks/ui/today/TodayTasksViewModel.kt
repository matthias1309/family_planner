package de.familyplanner.android.feature.tasks.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.tasks.domain.board.BoardLoad
import de.familyplanner.android.feature.tasks.domain.board.LoadBoardUseCase
import de.familyplanner.android.feature.tasks.domain.board.TaskLane
import de.familyplanner.android.feature.tasks.domain.board.TaskLimits
import de.familyplanner.android.feature.tasks.domain.board.isOverdue
import de.familyplanner.android.feature.tasks.domain.board.openCards
import de.familyplanner.android.feature.tasks.domain.board.overdueCount
import de.familyplanner.android.feature.tasks.domain.board.reviewCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/** Titel in „Heute“ mit Frist; [overdue] färbt ihn rot (REQ-TASK-027). */
data class TodayTitle(
    val title: String,
    val dueAt: Instant?,
    val overdue: Boolean,
)

/** Aufgaben für „Heute“ (REQ-TASK-002); offline aus dem lokalen Stand (ADR-0011). */
data class TodayTasksUiState(
    val isLoading: Boolean = true,
    val isParent: Boolean = false,
    val openCount: Int = 0,
    val titles: List<TodayTitle> = emptyList(),
    val overdueCount: Int = 0,
    val rejectedCount: Int = 0,
    val reviewCount: Int = 0,
    val unavailable: Boolean = false,
)

class TodayTasksViewModel(
    private val sessions: SessionStore,
    private val loadBoard: LoadBoardUseCase,
    private val clock: Clock = Clock.system(BerlinZone),
) : ViewModel() {
    private val _state = MutableStateFlow(TodayTasksUiState(isParent = sessions.current?.role == Role.PARENT))
    val state: StateFlow<TodayTasksUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val lanes =
                when (val result = loadBoard()) {
                    is BoardLoad.Fresh -> result.board.lanes
                    is BoardLoad.Cached -> result.board.lanes
                    is BoardLoad.Failed -> null
                }
            _state.value = lanes?.let(::summarize) ?: _state.value.copy(isLoading = false, unavailable = true)
        }
    }

    private fun summarize(lanes: List<TaskLane>): TodayTasksUiState {
        val session = sessions.current
        val parent = session?.role == Role.PARENT
        val now = clock.instant()
        val own = lanes.firstOrNull { it.memberId == session?.memberId }
        val open = openCards(own, now)
        return TodayTasksUiState(
            isLoading = false,
            isParent = parent,
            openCount = open.size,
            titles = open.take(TaskLimits.TODAY_TITLES).map { TodayTitle(it.title, it.dueAt, it.isOverdue(now)) },
            overdueCount = if (parent) overdueCount(lanes, now) else overdueCount(listOfNotNull(own), now),
            rejectedCount = open.count { it.rejectionReason != null },
            reviewCount = reviewCount(lanes),
        )
    }
}
