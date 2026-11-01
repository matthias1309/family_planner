package de.familyplanner.android.feature.tasks.domain

import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.domain.reminder.RestoreTaskRemindersUseCase
import de.familyplanner.android.feature.tasks.domain.reminder.ShowTaskReminderUseCase
import de.familyplanner.android.feature.tasks.fakes.FakeTaskNotifier
import de.familyplanner.android.feature.tasks.fakes.FakeTaskReminderScheduler
import de.familyplanner.android.feature.tasks.fakes.InMemoryBoardCache
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.childSession
import de.familyplanner.android.feature.tasks.fakes.lane
import de.familyplanner.android.feature.tasks.fakes.noon
import de.familyplanner.android.feature.tasks.fakes.parentSession
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskReminderUseCasesTest {
    private val cache = InMemoryBoardCache()
    private val notifier = FakeTaskNotifier()
    private val scheduler = FakeTaskReminderScheduler()
    private val clock = Clock.fixed(noon, ZoneOffset.UTC)
    private val due: Instant = noon.plus(Duration.ofHours(2))

    private fun show(session: de.familyplanner.android.core.data.session.Session = childSession) =
        ShowTaskReminderUseCase(InMemorySessionStore(session), cache, notifier, clock)

    private fun seed(vararg cards: de.familyplanner.android.feature.tasks.domain.board.TaskCard) =
        cache.seed("c-1", lane("c-1", "Ben", *cards))

    @Test
    fun `REQ-TASK-031 reminder fires for todo with unchanged due date`() =
        runTest {
            seed(card("a", title = "Bad", dueAt = due))

            show()("a", due)

            assertEquals("Bad", notifier.reminders.single().title)
        }

    @Test
    fun `REQ-TASK-031 reminder is dropped after report`() =
        runTest {
            seed(card("a", status = TaskStatus.REVIEW, dueAt = due))

            show()("a", due)

            assertEquals(emptyList(), notifier.reminders)
        }

    @Test
    fun `REQ-TASK-031 reminder is dropped when due date changed or removed`() =
        runTest {
            seed(card("a", dueAt = due.plusSeconds(3600)), card("b"))

            show()("a", due)
            show()("b", due)

            assertEquals(emptyList(), notifier.reminders)
        }

    @Test
    fun `REQ-TASK-031 reminder is dropped for removed card, past due date and parents`() =
        runTest {
            seed(card("a", dueAt = noon))

            show()("gone", due)
            show()("a", noon)
            show(parentSession)("a", noon)

            assertEquals(emptyList(), notifier.reminders)
        }

    @Test
    fun `REQ-TASK-031 restore schedules future reminders from cache`() =
        runTest {
            seed(card("a", dueAt = noon.plus(Duration.ofHours(6))), card("old", dueAt = due))

            RestoreTaskRemindersUseCase(InMemorySessionStore(childSession), cache, scheduler, clock)()

            assertEquals(listOf("a"), scheduler.scheduled.single().map { it.cardId })
        }
}
