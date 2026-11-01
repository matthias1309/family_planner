package de.familyplanner.android.feature.tasks.domain.reminder

import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.tasks.domain.board.BoardCache
import de.familyplanner.android.feature.tasks.domain.board.TaskNotifier
import de.familyplanner.android.feature.tasks.domain.board.TaskReminderScheduler
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.domain.board.taskReminders
import de.familyplanner.android.feature.tasks.domain.notice.awaitSession
import java.time.Clock
import java.time.Instant

/** Erinnerung an Karte [cardId]; [at] = Fristende − 2 h (REQ-TASK-031). */
data class TaskReminder(
    val cardId: String,
    val title: String,
    val dueAt: Instant,
    val at: Instant,
)

/**
 * Alarm ist fällig: erinnern, solange die Karte laut lokalem Stand noch dem Kind gehört, in TODO ist und die Frist unverändert
 * und nicht vorbei ist. Veraltete Alarme (gemeldet, entfernt, Frist geändert, abgemeldet) werden hier verworfen.
 */
class ShowTaskReminderUseCase(
    private val sessions: SessionStore,
    private val cache: BoardCache,
    private val notifier: TaskNotifier,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    suspend operator fun invoke(
        cardId: String,
        dueAt: Instant,
    ) {
        val session = sessions.awaitSession()?.takeIf { it.role != Role.PARENT } ?: return
        val card =
            cache
                .read(session.memberId)
                ?.lanes
                ?.firstOrNull { it.memberId == session.memberId }
                ?.cards
                ?.firstOrNull { it.id == cardId }
                ?: return
        val now = clock.instant()
        val due = card.status == TaskStatus.TODO && card.dueAt == dueAt && dueAt.isAfter(now)
        if (due) notifier.remind(TaskReminder(card.id, card.title, dueAt, now))
    }
}

/** Nach Neustart oder App-Update: künftige Erinnerungen aus dem lokalen Stand neu einplanen. */
class RestoreTaskRemindersUseCase(
    private val sessions: SessionStore,
    private val cache: BoardCache,
    private val scheduler: TaskReminderScheduler,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    suspend operator fun invoke() {
        val session = sessions.awaitSession() ?: return
        val lanes = cache.read(session.memberId)?.lanes ?: return
        scheduler.schedule(taskReminders(lanes, session.memberId, session.role == Role.PARENT, clock.instant()))
    }
}
