package de.familyplanner.android.feature.meals.domain.reminder

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.fakes.FakeCookingRepository
import de.familyplanner.android.feature.meals.fakes.FakeMealPlanRepository
import de.familyplanner.android.feature.meals.fakes.FakeNotifier
import de.familyplanner.android.feature.meals.fakes.FakeReminderScheduler
import de.familyplanner.android.feature.meals.fakes.FakeReminderStore
import de.familyplanner.android.feature.meals.fakes.benDeadline
import de.familyplanner.android.feature.meals.fakes.childDish
import de.familyplanner.android.feature.meals.fakes.childSession
import de.familyplanner.android.feature.meals.fakes.cookStatus
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.statusOf
import de.familyplanner.android.feature.meals.fakes.weekPlanOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UseCasesTest {
    // Mo 12.10.2026, 08:00 Berlin: Planwoche ist der 19.10., Bens Frist Do 15.10. 16:00.
    private val monday = Clock.fixed(Instant.parse("2026-10-12T06:00:00Z"), ZoneId.of("Europe/Berlin"))

    private val sessions = InMemorySessionStore(childSession)
    private val cooking = FakeCookingRepository()
    private val plan = FakeMealPlanRepository()
    private val store = FakeReminderStore()
    private val scheduler = FakeReminderScheduler()
    private val notifier = FakeNotifier()

    private fun sync() = SyncRemindersUseCase(sessions, cooking, plan, store, scheduler, notifier, monday)

    private fun snapshot(
        memberId: String = "c-1",
        state: CookState? = CookState.NOT_SUBMITTED,
        reminders: List<Reminder> = emptyList(),
    ) = ReminderSnapshot(memberId, planWeek, benDeadline, state, reminders, emptyMap())

    @Test
    fun `REQ-MEAL-050 sync schedules reminders for an open child`() =
        runTest {
            val outcome = sync()()

            assertEquals(SyncOutcome.SYNCED, outcome)
            assertEquals(3, scheduler.replaced.single().size)
            assertEquals(3, store.snapshot?.reminders?.size)
        }

    @Test
    fun `REQ-MEAL-050 sync clears reminders after submission`() =
        runTest {
            cooking.statusResult = { ApiResult.Success(statusOf(it, cookStatus(state = CookState.SUBMITTED, dishId = "d-2"))) }

            sync()()

            assertEquals(listOf(emptyList()), scheduler.replaced)
        }

    @Test
    fun `REQ-MEAL-050 sync keeps reminders when the server is unreachable`() =
        runTest {
            cooking.statusResult = { ApiResult.Failure(AppError.Network) }

            val outcome = sync()()

            assertEquals(SyncOutcome.UNREACHABLE, outcome)
            assertTrue(scheduler.replaced.isEmpty())
            assertEquals(0, store.writes)
        }

    @Test
    fun `REQ-MEAL-050 signed out cancels reminders`() =
        runTest {
            sessions.setCurrent(null)

            assertEquals(SyncOutcome.SIGNED_OUT, sync()())
            assertEquals(1, scheduler.cancelled)
            assertEquals(1, store.cleared)
        }

    @Test
    fun `REQ-MEAL-050 unauthorized cancels reminders`() =
        runTest {
            plan.weekResult = { ApiResult.Failure(AppError.Unauthorized) }

            assertEquals(SyncOutcome.SIGNED_OUT, sync()())
            assertEquals(1, scheduler.cancelled)
        }

    @Test
    fun `REQ-MEAL-052 child is told about a rejected dish after the baseline`() =
        runTest {
            val rejected = childDish.copy(status = DishStatus.REJECTED, rejectionReason = "kein Gemüse")
            plan.weekResult = { ApiResult.Success(weekPlanOf(it, childDish)) }
            sync()()
            plan.weekResult = { ApiResult.Success(weekPlanOf(it, rejected)) }
            sync()()

            assertEquals(listOf<StatusNotice>(StatusNotice.Rejected("d-2", "Curry", rejected.date, "kein Gemüse")), notifier.notices)
        }

    @Test
    fun `REQ-MEAL-052 snapshot of another member is treated as baseline`() =
        runTest {
            store.snapshot = snapshot(memberId = "p-1")
            plan.weekResult = { ApiResult.Success(weekPlanOf(it, childDish.copy(status = DishStatus.APPROVED))) }

            sync()()

            assertTrue(notifier.notices.isEmpty())
        }

    @Test
    fun `REQ-MEAL-053 parent sync announces submissions and schedules nothing`() =
        runTest {
            sessions.setCurrent(parentSession)
            plan.weekResult = { ApiResult.Success(weekPlanOf(it)) }
            sync()()
            plan.weekResult = { ApiResult.Success(weekPlanOf(it, childDish)) }
            sync()()

            val notice = notifier.notices.single() as StatusNotice.Submitted
            assertEquals("Ben", notice.cookName)
            assertTrue(scheduler.replaced.all { it.isEmpty() })
        }

    @Test
    fun `REQ-MEAL-050 due reminder is shown while cooking is still open`() =
        runTest {
            store.snapshot = snapshot()

            ShowDueReminderUseCase(sessions, store, notifier, monday)(planWeek)

            assertEquals(listOf(DeadlineReminder(benDeadline, false)), notifier.reminders)
        }

    @Test
    fun `REQ-MEAL-050 due reminder says revise after a rejection`() =
        runTest {
            store.snapshot = snapshot(state = CookState.REJECTED)

            ShowDueReminderUseCase(sessions, store, notifier, monday)(planWeek)

            assertTrue(notifier.reminders.single().rejected)
        }

    @Test
    fun `REQ-MEAL-050 due reminder is suppressed after submission, for other weeks and members`() =
        runTest {
            val show = ShowDueReminderUseCase(sessions, store, notifier, monday)
            store.snapshot = snapshot(state = CookState.SUBMITTED)
            show(planWeek)
            store.snapshot = snapshot()
            show(planWeek.plusWeeks(1))
            store.snapshot = snapshot(memberId = "c-9")
            show(planWeek)

            assertTrue(notifier.reminders.isEmpty())
        }

    @Test
    fun `REQ-MEAL-050 restore reschedules only future reminders`() =
        runTest {
            val past = Reminder(planWeek, 3, Instant.parse("2026-10-11T14:00:00Z"))
            val future =
                listOf(
                    Reminder(planWeek, 2, Instant.parse("2026-10-13T14:00:00Z")),
                    Reminder(planWeek, 1, Instant.parse("2026-10-14T14:00:00Z")),
                )
            store.snapshot = snapshot(reminders = listOf(past) + future)

            RestoreRemindersUseCase(store, scheduler, monday)()

            assertEquals(listOf(future), scheduler.replaced)
        }

    @Test
    fun `REQ-MEAL-051 pending cooking is loaded from the local snapshot`() =
        runTest {
            store.snapshot = snapshot(state = CookState.REJECTED)

            val pending = LoadPendingCookingUseCase(sessions, store, monday)()

            assertEquals(PendingCooking(benDeadline, true), pending)
        }

    @Test
    fun `REQ-MEAL-051 no pending cooking when submitted or from another week`() =
        runTest {
            val load = LoadPendingCookingUseCase(sessions, store, monday)
            store.snapshot = snapshot(state = CookState.SUBMITTED)
            assertNull(load())
            store.snapshot = snapshot().copy(weekStart = planWeek.minusWeeks(1))
            assertNull(load())
        }
}
