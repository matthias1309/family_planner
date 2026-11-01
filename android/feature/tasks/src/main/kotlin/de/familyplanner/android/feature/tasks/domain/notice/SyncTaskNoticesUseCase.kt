package de.familyplanner.android.feature.tasks.domain.notice

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.data.session.SessionState
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.tasks.domain.board.BoardCache
import de.familyplanner.android.feature.tasks.domain.board.BoardSnapshot
import de.familyplanner.android.feature.tasks.domain.board.TaskNotifier
import de.familyplanner.android.feature.tasks.domain.board.TaskReminderScheduler
import de.familyplanner.android.feature.tasks.domain.board.TaskRepository
import de.familyplanner.android.feature.tasks.domain.board.taskReminders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

enum class TaskSyncOutcome { SYNCED, UNREACHABLE, SIGNED_OUT }

/** Angemeldete Session, nachdem sie aus dem Speicher geladen ist (im Worker evtl. noch `Loading`). */
internal suspend fun SessionStore.awaitSession(): Session? =
    (
        state.first {
            it !is SessionState.Loading
        } as? SessionState.SignedIn
    )?.session

/** Gleicht das Board mit dem Server ab: Hinweise und lokaler Stand (REQ-TASK-030). */
class SyncTaskNoticesUseCase(
    private val sessions: SessionStore,
    private val repository: TaskRepository,
    private val cache: BoardCache,
    private val notifier: TaskNotifier,
    private val scheduler: TaskReminderScheduler,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    // Periodischer und sofortiger Abgleich können gleichzeitig laufen; sonst gäbe es doppelte Meldungen.
    private val mutex = Mutex()

    suspend operator fun invoke(): TaskSyncOutcome =
        mutex.withLock {
            val session = sessions.awaitSession() ?: return@withLock signOut()
            val lanes =
                when (val result = repository.board()) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Failure ->
                        return@withLock if (result.error == AppError.Unauthorized) signOut() else TaskSyncOutcome.UNREACHABLE
                }
            val previous = cache.read(session.memberId)?.lanes
            taskNotices(session.role == Role.PARENT, session.memberId, previous, lanes).forEach(notifier::announce)
            val now = clock.instant()
            cache.write(session.memberId, BoardSnapshot(lanes, now))
            scheduler.schedule(taskReminders(lanes, session.memberId, session.role == Role.PARENT, now))
            TaskSyncOutcome.SYNCED
        }

    private suspend fun signOut(): TaskSyncOutcome {
        cache.clear()
        return TaskSyncOutcome.SIGNED_OUT
    }
}
