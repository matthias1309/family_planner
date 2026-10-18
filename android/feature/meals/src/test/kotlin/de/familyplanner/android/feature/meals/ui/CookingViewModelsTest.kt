package de.familyplanner.android.feature.meals.ui

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceDay
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceSource
import de.familyplanner.android.feature.meals.domain.cooking.CancelCookingUseCase
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.cooking.CookingUseCases
import de.familyplanner.android.feature.meals.domain.cooking.LoadWeekStatusUseCase
import de.familyplanner.android.feature.meals.domain.cooking.ReviewDishUseCase
import de.familyplanner.android.feature.meals.domain.cooking.WithdrawCancellationUseCase
import de.familyplanner.android.feature.meals.domain.plan.DeleteDishUseCase
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.plan.DishUseCases
import de.familyplanner.android.feature.meals.domain.plan.EditorMode
import de.familyplanner.android.feature.meals.domain.plan.LoadDishEditorUseCase
import de.familyplanner.android.feature.meals.domain.plan.LoadWeekPlanUseCase
import de.familyplanner.android.feature.meals.domain.plan.SaveDishUseCase
import de.familyplanner.android.feature.meals.domain.recipe.LoadRecipeUseCase
import de.familyplanner.android.feature.meals.fakes.FakeAttendanceRepository
import de.familyplanner.android.feature.meals.fakes.FakeCookingRepository
import de.familyplanner.android.feature.meals.fakes.FakeMealPlanRepository
import de.familyplanner.android.feature.meals.fakes.FakeRecipeRepository
import de.familyplanner.android.feature.meals.fakes.InMemoryPlanCache
import de.familyplanner.android.feature.meals.fakes.childDish
import de.familyplanner.android.feature.meals.fakes.childSession
import de.familyplanner.android.feature.meals.fakes.childWeek
import de.familyplanner.android.feature.meals.fakes.cookStatus
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.statusOf
import de.familyplanner.android.feature.meals.fakes.weekOf
import de.familyplanner.android.feature.meals.fakes.weekPlanOf
import de.familyplanner.android.feature.meals.ui.dish.DishEditArgs
import de.familyplanner.android.feature.meals.ui.dish.DishEditEvent
import de.familyplanner.android.feature.meals.ui.dish.DishEditViewModel
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanEvent
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CookingViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeMealPlanRepository()
    private val attendance = FakeAttendanceRepository()
    private val cooking = FakeCookingRepository()
    private val cache = InMemoryPlanCache()
    private var sessions = InMemorySessionStore(parentSession)

    // Samstag, 17.10.2026 → Planwoche 19.10.; Frist von Ben (Do 15.10., 16:00) ist vorbei.
    private val saturday = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val wednesdayBeforeDeadline = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val tuesday = planWeek.plusDays(1)
    private val wednesday = planWeek.plusDays(2)
    private val thursday = planWeek.plusDays(3)

    private fun login(session: Session) {
        sessions = InMemorySessionStore(session)
    }

    private fun benPresentOn(vararg days: LocalDate) {
        val present = days.toSet()
        attendance.weekResult = {
            ApiResult.Success(
                weekOf(
                    childWeek().copy(
                        days =
                            (0L..6L).map {
                                AttendanceDay(
                                    planWeek.plusDays(it),
                                    planWeek.plusDays(it) in present,
                                    AttendanceSource.PATTERN,
                                )
                            },
                    ),
                    start = it,
                ),
            )
        }
    }

    private fun planWith(vararg dishes: de.familyplanner.android.feature.meals.domain.plan.Dish) {
        repository.weekResult = { ApiResult.Success(weekPlanOf(it, *dishes)) }
    }

    private fun cookingUseCases() =
        CookingUseCases(LoadWeekStatusUseCase(cooking, repository), CancelCookingUseCase(cooking), WithdrawCancellationUseCase(cooking))

    private fun planViewModel(clock: Clock = saturday) =
        WeekPlanViewModel(sessions, LoadWeekPlanUseCase(repository, cache, sessions, clock), cookingUseCases(), clock)

    private fun editViewModel(
        date: LocalDate = tuesday,
        dishId: String? = null,
        cookId: String? = null,
        clock: Clock = saturday,
    ) = DishEditViewModel(
        DishEditArgs(date, dishId, cookId),
        sessions,
        DishUseCases(
            LoadDishEditorUseCase(repository, attendance),
            SaveDishUseCase(repository),
            DeleteDishUseCase(repository),
            ReviewDishUseCase(cooking),
            LoadRecipeUseCase(FakeRecipeRepository()),
        ),
        clock,
    )

    // ---- Gericht-Screen ----

    @Test
    fun `REQ-MEAL-020 child editor fixes cook to self`() {
        login(childSession)

        val state = editViewModel().state.value

        assertEquals("c-1", state.cookId)
        assertEquals(EditorMode.CHILD, state.mode)
        assertTrue(state.ingredientsRequired)
        assertFalse(state.canDelete)
    }

    @Test
    fun `REQ-MEAL-021 child editor allows only own present days`() {
        login(childSession)
        benPresentOn(tuesday, wednesday, thursday)

        val state = editViewModel(date = planWeek).state.value

        assertEquals(setOf(tuesday, wednesday, thursday), state.allowedDates)
        assertEquals(tuesday, state.date)
    }

    @Test
    fun `REQ-MEAL-020 child saving without ingredients shows validation`() {
        login(childSession)
        val viewModel = editViewModel()
        viewModel.onEvent(DishEditEvent.NameChanged("Curry"))

        viewModel.onEvent(DishEditEvent.Save)

        assertIs<AppError.Validation>(viewModel.state.value.error)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `REQ-MEAL-020 child saves dish with ingredients for own day`() {
        login(childSession)
        val viewModel = editViewModel()
        viewModel.onEvent(DishEditEvent.NameChanged("Curry"))
        viewModel.onEvent(DishEditEvent.IngredientsChanged("Reis\nErbsen"))

        viewModel.onEvent(DishEditEvent.Save)

        assertEquals("c-1", repository.created.single().cookId)
        assertTrue(viewModel.state.value.isDone)
    }

    @Test
    fun `REQ-MEAL-020 late child sees after deadline hint`() {
        login(childSession)

        assertTrue(editViewModel(clock = saturday).state.value.isLate)
        assertFalse(editViewModel(clock = wednesdayBeforeDeadline).state.value.isLate)
    }

    @Test
    fun `REQ-MEAL-022 child editing approved dish sees re-approval hint`() {
        login(childSession)
        planWith(childDish.copy(status = DishStatus.APPROVED))

        val state = editViewModel(dishId = "d-2").state.value

        assertEquals(DishStatus.APPROVED, state.existing?.status)
        assertEquals(EditorMode.CHILD, state.mode)
        assertEquals("Curry", state.name)
    }

    @Test
    fun `REQ-MEAL-030 rejected dish shows reason to the child`() {
        login(childSession)
        planWith(childDish.copy(status = DishStatus.REJECTED, rejectionReason = "Mehr Gemüse"))

        assertEquals(
            "Mehr Gemüse",
            editViewModel(dishId = "d-2")
                .state.value.existing
                ?.rejectionReason,
        )
    }

    @Test
    fun `REQ-MEAL-021 child moves own invalid dish`() {
        login(childSession)
        planWith(childDish.copy(status = DishStatus.APPROVED, dayValid = false))
        benPresentOn(wednesday)
        val viewModel = editViewModel(dishId = "d-2")
        assertFalse(viewModel.state.value.canSave)

        viewModel.onEvent(DishEditEvent.DateSelected(wednesday))
        viewModel.onEvent(DishEditEvent.Save)

        assertEquals("d-2" to wednesday, repository.updated.single().let { it.first to it.second.date })
    }

    @Test
    fun `REQ-MEAL-020 child that does not cook gets a message`() {
        login(childSession)
        attendance.weekResult = { ApiResult.Success(weekOf(childWeek(cooks = false, deadline = null), start = it)) }

        val error = editViewModel().state.value.loadError

        assertEquals(AppError.Validation("Du kochst in dieser Woche nicht."), error)
    }

    @Test
    fun `REQ-MEAL-031 parent preselects child from args and only its days`() {
        benPresentOn(wednesday, thursday)

        val state = editViewModel(date = tuesday, cookId = "c-1").state.value

        assertEquals("c-1", state.cookId)
        assertEquals(setOf(wednesday, thursday), state.allowedDates)
        assertEquals(wednesday, state.date)
        assertEquals(EditorMode.PARENT_EDIT, state.mode)
    }

    @Test
    fun `REQ-MEAL-031 switching cook to child drops absent day`() {
        benPresentOn(wednesday, thursday)
        val viewModel = editViewModel(date = tuesday)
        assertEquals(tuesday, viewModel.state.value.date)

        viewModel.onEvent(DishEditEvent.CookSelected("c-1"))

        assertEquals(wednesday, viewModel.state.value.date)
        viewModel.onEvent(DishEditEvent.CookSelected("p-1"))
        assertEquals(wednesday, viewModel.state.value.date)
        assertTrue(tuesday in viewModel.state.value.allowedDates)
    }

    @Test
    fun `REQ-MEAL-031 parent saving child dish requires ingredients`() {
        val viewModel = editViewModel(cookId = "c-1")
        viewModel.onEvent(DishEditEvent.NameChanged("Curry"))

        viewModel.onEvent(DishEditEvent.Save)

        assertIs<AppError.Validation>(viewModel.state.value.error)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `REQ-MEAL-030 parent approves submitted dish and finishes`() {
        planWith(childDish)
        val viewModel = editViewModel(dishId = "d-2")
        assertEquals(EditorMode.PARENT_REVIEW, viewModel.state.value.mode)

        viewModel.onEvent(DishEditEvent.Approve)

        assertEquals(listOf("d-2"), cooking.approved)
        assertTrue(viewModel.state.value.isDone)
    }

    @Test
    fun `REQ-MEAL-030 parent rejects with reason and finishes`() {
        planWith(childDish)
        val viewModel = editViewModel(dishId = "d-2")

        viewModel.onEvent(DishEditEvent.RequestReject)
        assertTrue(viewModel.state.value.showRejectDialog)
        viewModel.onEvent(DishEditEvent.RejectReasonChanged(" Mehr Gemüse "))
        viewModel.onEvent(DishEditEvent.ConfirmReject)

        assertEquals(listOf("d-2" to "Mehr Gemüse"), cooking.rejected)
        assertTrue(viewModel.state.value.isDone)
        assertFalse(viewModel.state.value.showRejectDialog)
    }

    @Test
    fun `REQ-MEAL-030 reject without reason keeps dialog with message`() {
        planWith(childDish)
        val viewModel = editViewModel(dishId = "d-2")
        viewModel.onEvent(DishEditEvent.RequestReject)

        viewModel.onEvent(DishEditEvent.ConfirmReject)

        assertTrue(viewModel.state.value.showRejectDialog)
        assertIs<AppError.Validation>(viewModel.state.value.error)
        assertTrue(cooking.rejected.isEmpty())
        viewModel.onEvent(DishEditEvent.CancelReject)
        assertFalse(viewModel.state.value.showRejectDialog)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `REQ-MEAL-030 approving an already reviewed dish shows conflict`() {
        planWith(childDish)
        cooking.approveResult = ApiResult.Failure(AppError.Conflict("Das Gericht wartet nicht auf Freigabe"))
        val viewModel = editViewModel(dishId = "d-2")

        viewModel.onEvent(DishEditEvent.Approve)

        assertEquals(AppError.Conflict("Das Gericht wartet nicht auf Freigabe"), viewModel.state.value.error)
        assertFalse(viewModel.state.value.isDone)
    }

    @Test
    fun `REQ-MEAL-030 rejecting an already reviewed dish closes the dialog with the message`() {
        planWith(childDish)
        cooking.rejectResult = ApiResult.Failure(AppError.Conflict("Das Gericht wartet nicht auf Freigabe"))
        val viewModel = editViewModel(dishId = "d-2")
        viewModel.onEvent(DishEditEvent.RequestReject)
        viewModel.onEvent(DishEditEvent.RejectReasonChanged("Zu scharf"))

        viewModel.onEvent(DishEditEvent.ConfirmReject)

        assertFalse(viewModel.state.value.showRejectDialog)
        assertIs<AppError.Conflict>(viewModel.state.value.error)
    }

    @Test
    fun `REQ-MEAL-021 move mode locks fields and cook`() {
        planWith(childDish.copy(dayValid = false))
        val viewModel = editViewModel(dishId = "d-2")

        viewModel.onEvent(DishEditEvent.NameChanged("Anderes"))
        viewModel.onEvent(DishEditEvent.IngredientsChanged("Nichts"))
        viewModel.onEvent(DishEditEvent.CookSelected("p-1"))

        val state = viewModel.state.value
        assertEquals(EditorMode.PARENT_MOVE, state.mode)
        assertTrue(state.fieldsLocked)
        assertEquals("Curry", state.name)
        assertEquals("Reis", state.ingredientsText)
        assertEquals("c-1", state.cookId)
    }

    @Test
    fun `REQ-MEAL-021 move mode cannot save the same invalid day`() {
        planWith(childDish.copy(dayValid = false))
        benPresentOn(wednesday)
        val viewModel = editViewModel(dishId = "d-2")

        assertFalse(viewModel.state.value.canSave)
        viewModel.onEvent(DishEditEvent.Save)

        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun `REQ-MEAL-021 move mode saves a present day`() {
        planWith(childDish.copy(dayValid = false))
        benPresentOn(wednesday)
        val viewModel = editViewModel(dishId = "d-2")

        viewModel.onEvent(DishEditEvent.DateSelected(wednesday))
        viewModel.onEvent(DishEditEvent.Save)

        val (id, draft) = repository.updated.single()
        assertEquals("d-2", id)
        assertEquals(wednesday, draft.date)
        assertEquals("c-1", draft.cookId)
        assertTrue(viewModel.state.value.isDone)
    }

    @Test
    fun `REQ-MEAL-021 move mode allows delete`() {
        planWith(childDish.copy(dayValid = false))
        val viewModel = editViewModel(dishId = "d-2")
        assertTrue(viewModel.state.value.canDelete)

        viewModel.onEvent(DishEditEvent.ConfirmDelete)

        assertEquals(listOf("d-2"), repository.deleted)
    }

    @Test
    fun `REQ-MEAL-021 move mode hides review`() {
        planWith(childDish.copy(dayValid = false))
        val viewModel = editViewModel(dishId = "d-2")

        viewModel.onEvent(DishEditEvent.Approve)

        assertTrue(cooking.approved.isEmpty())
        assertFalse(viewModel.state.value.mode == EditorMode.PARENT_REVIEW)
    }

    @Test
    fun `REQ-MEAL-020 child cannot delete`() {
        login(childSession)
        planWith(childDish)
        val viewModel = editViewModel(dishId = "d-2")

        viewModel.onEvent(DishEditEvent.ConfirmDelete)

        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `REQ-MEAL-030 double approve sends once`() {
        planWith(childDish)
        val gate = CompletableDeferred<Unit>()
        cooking.approveGate = gate
        val viewModel = editViewModel(dishId = "d-2")

        viewModel.onEvent(DishEditEvent.Approve)
        viewModel.onEvent(DishEditEvent.Approve)
        gate.complete(Unit)

        assertEquals(listOf("d-2"), cooking.approved)
    }

    // ---- Essensplan ----

    @Test
    fun `REQ-MEAL-033 plan week loads status in parallel`() {
        repository.weekGates[planWeek] = CompletableDeferred()
        val viewModel = planViewModel()

        viewModel.onEvent(WeekPlanEvent.NextWeek)

        assertEquals(listOf(planWeek), cooking.requestedWeeks)
        assertNotNull(viewModel.state.value.status)
        assertNull(viewModel.state.value.week)
    }

    @Test
    fun `REQ-MEAL-033 this week loads no status`() {
        val viewModel = planViewModel()

        assertTrue(cooking.requestedWeeks.isEmpty())
        assertNull(viewModel.state.value.status)
        assertFalse(viewModel.state.value.showsStatus)
    }

    @Test
    fun `REQ-MEAL-033 prefetch loads no status`() {
        planViewModel()

        assertTrue(planWeek in repository.requestedWeeks)
        assertTrue(cooking.requestedWeeks.isEmpty())
    }

    @Test
    fun `REQ-MEAL-033 late status of previous week is ignored`() {
        val gate = CompletableDeferred<Unit>()
        cooking.statusGates[planWeek] = gate
        val viewModel = planViewModel()
        viewModel.onEvent(WeekPlanEvent.NextWeek)
        viewModel.onEvent(WeekPlanEvent.NextWeek)

        gate.complete(Unit)

        assertEquals(
            planWeek.plusWeeks(1),
            viewModel.state.value.status
                ?.weekStart,
        )
    }

    @Test
    fun `REQ-MEAL-033 offline plan hides status`() {
        val viewModel = planViewModel()
        viewModel.onEvent(WeekPlanEvent.NextWeek)
        repository.weekResult = { ApiResult.Failure(AppError.Network) }

        viewModel.onEvent(WeekPlanEvent.Refresh)

        assertTrue(viewModel.state.value.isOffline)
        assertFalse(viewModel.state.value.showsStatus)
    }

    @Test
    fun `REQ-MEAL-033 status error is kept for the card`() {
        cooking.statusResult = { ApiResult.Failure(AppError.Server) }
        val viewModel = planViewModel()

        viewModel.onEvent(WeekPlanEvent.NextWeek)

        assertEquals(AppError.Server, viewModel.state.value.statusError)
        assertNull(viewModel.state.value.status)
    }

    @Test
    fun `REQ-MEAL-020 child sees own cooking`() {
        login(childSession)
        cooking.statusResult = { ApiResult.Success(statusOf(it, cookStatus("c-1"), cookStatus("c-2"))) }
        val viewModel = planViewModel()

        viewModel.onEvent(WeekPlanEvent.NextWeek)

        assertEquals(
            "c-1",
            viewModel.state.value.myCooking
                ?.status
                ?.memberId,
        )
    }

    @Test
    fun `REQ-MEAL-033 parent has no own cooking card`() {
        val viewModel = planViewModel()

        viewModel.onEvent(WeekPlanEvent.NextWeek)

        assertNull(viewModel.state.value.myCooking)
    }

    @Test
    fun `REQ-MEAL-033 go to plan week switches week`() {
        val viewModel = planViewModel()
        assertTrue(viewModel.state.value.showsPlanWeekLink)

        viewModel.onEvent(WeekPlanEvent.GoToPlanWeek)

        assertEquals(planWeek, viewModel.state.value.weekStart)
        assertFalse(viewModel.state.value.showsPlanWeekLink)
        assertEquals(listOf(planWeek), cooking.requestedWeeks)
    }

    @Test
    fun `REQ-MEAL-023 child cancels own cooking with reason and reloads`() {
        login(childSession)
        val viewModel = planViewModel()
        viewModel.onEvent(WeekPlanEvent.GoToPlanWeek)

        viewModel.onEvent(WeekPlanEvent.RequestCancel("c-1"))
        viewModel.onEvent(WeekPlanEvent.CancelReasonChanged("krank"))
        viewModel.onEvent(WeekPlanEvent.ConfirmCancel)

        assertEquals(listOf(Triple(planWeek, "c-1", "krank")), cooking.cancelled)
        assertNull(viewModel.state.value.cancelTarget)
        assertEquals(2, cooking.requestedWeeks.size)
    }

    @Test
    fun `REQ-MEAL-023 cancel without reason keeps dialog`() {
        val viewModel = planViewModel()
        viewModel.onEvent(WeekPlanEvent.GoToPlanWeek)
        viewModel.onEvent(WeekPlanEvent.RequestCancel("c-1"))

        viewModel.onEvent(WeekPlanEvent.ConfirmCancel)

        assertEquals("c-1", viewModel.state.value.cancelTarget)
        assertIs<AppError.Validation>(viewModel.state.value.actionError)
        assertTrue(cooking.cancelled.isEmpty())
        viewModel.onEvent(WeekPlanEvent.DismissCancel)
        assertNull(viewModel.state.value.cancelTarget)
        assertNull(viewModel.state.value.actionError)
    }

    @Test
    fun `REQ-MEAL-023 parent cancels for child`() {
        val viewModel = planViewModel()
        viewModel.onEvent(WeekPlanEvent.GoToPlanWeek)
        viewModel.onEvent(WeekPlanEvent.RequestCancel("c-1"))
        viewModel.onEvent(WeekPlanEvent.CancelReasonChanged("Ferien"))

        viewModel.onEvent(WeekPlanEvent.ConfirmCancel)

        assertEquals(listOf(Triple(planWeek, "c-1", "Ferien")), cooking.cancelled)
    }

    @Test
    fun `REQ-MEAL-023 failed cancel keeps dialog with server message`() {
        cooking.cancelResult = ApiResult.Failure(AppError.Forbidden)
        val viewModel = planViewModel()
        viewModel.onEvent(WeekPlanEvent.GoToPlanWeek)
        viewModel.onEvent(WeekPlanEvent.RequestCancel("c-1"))
        viewModel.onEvent(WeekPlanEvent.CancelReasonChanged("krank"))

        viewModel.onEvent(WeekPlanEvent.ConfirmCancel)

        assertEquals(AppError.Forbidden, viewModel.state.value.actionError)
        assertEquals("c-1", viewModel.state.value.cancelTarget)
    }

    @Test
    fun `REQ-MEAL-023 withdraw reloads`() {
        cooking.statusResult = { ApiResult.Success(statusOf(it, cookStatus("c-1", CookState.CANCELLED, cancellationReason = "krank"))) }
        val viewModel = planViewModel()
        viewModel.onEvent(WeekPlanEvent.GoToPlanWeek)

        viewModel.onEvent(WeekPlanEvent.Withdraw("c-1"))

        assertEquals(listOf(planWeek to "c-1"), cooking.withdrawn)
        assertEquals(2, cooking.requestedWeeks.size)
    }

    @Test
    fun `REQ-MEAL-023 failed withdraw shows the error`() {
        cooking.withdrawResult = ApiResult.Failure(AppError.NotFound)
        val viewModel = planViewModel()
        viewModel.onEvent(WeekPlanEvent.GoToPlanWeek)

        viewModel.onEvent(WeekPlanEvent.Withdraw("c-1"))

        assertEquals(AppError.NotFound, viewModel.state.value.actionError)
    }

    @Test
    fun `REQ-MEAL-023 double confirm cancels once`() {
        val gate = CompletableDeferred<Unit>()
        cooking.cancelGate = gate
        val viewModel = planViewModel()
        viewModel.onEvent(WeekPlanEvent.GoToPlanWeek)
        viewModel.onEvent(WeekPlanEvent.RequestCancel("c-1"))
        viewModel.onEvent(WeekPlanEvent.CancelReasonChanged("krank"))

        viewModel.onEvent(WeekPlanEvent.ConfirmCancel)
        viewModel.onEvent(WeekPlanEvent.ConfirmCancel)
        gate.complete(Unit)

        assertEquals(1, cooking.cancelled.size)
    }

    // ---- Review-Nachbesserungen ----

    private class MutableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("Europe/Berlin")

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    @Test
    fun `REQ-MEAL-033 plan week rolls over when the app stays open across monday`() {
        val clock = MutableClock(Instant.parse("2026-10-17T10:00:00Z"))
        val viewModel = planViewModel(clock)
        assertEquals(planWeek, viewModel.state.value.planWeek)

        clock.now = Instant.parse("2026-10-19T07:00:00Z")
        viewModel.onEvent(WeekPlanEvent.Refresh)

        assertEquals(planWeek.plusWeeks(1), viewModel.state.value.planWeek)
    }

    @Test
    fun `REQ-MEAL-030 reject outside review mode is ignored`() {
        planWith(childDish.copy(status = DishStatus.APPROVED))
        val viewModel = editViewModel(dishId = "d-2")
        assertEquals(EditorMode.PARENT_EDIT, viewModel.state.value.mode)

        viewModel.onEvent(DishEditEvent.RequestReject)
        viewModel.onEvent(DishEditEvent.ConfirmReject)
        viewModel.onEvent(DishEditEvent.Approve)

        assertFalse(viewModel.state.value.showRejectDialog)
        assertTrue(cooking.rejected.isEmpty())
        assertTrue(cooking.approved.isEmpty())
    }

    @Test
    fun `REQ-MEAL-030 child cannot reject or approve`() {
        login(childSession)
        planWith(childDish)
        val viewModel = editViewModel(dishId = "d-2")

        viewModel.onEvent(DishEditEvent.RequestReject)
        viewModel.onEvent(DishEditEvent.ConfirmReject)
        viewModel.onEvent(DishEditEvent.Approve)

        assertTrue(cooking.rejected.isEmpty())
        assertTrue(cooking.approved.isEmpty())
    }

    @Test
    fun `REQ-MEAL-023 child cannot cancel or withdraw for a sibling`() {
        login(childSession)
        val viewModel = planViewModel()
        viewModel.onEvent(WeekPlanEvent.GoToPlanWeek)

        viewModel.onEvent(WeekPlanEvent.RequestCancel("c-2"))
        viewModel.onEvent(WeekPlanEvent.Withdraw("c-2"))

        assertNull(viewModel.state.value.cancelTarget)
        assertTrue(cooking.cancelled.isEmpty())
        assertTrue(cooking.withdrawn.isEmpty())
    }

    @Test
    fun `REQ-MEAL-023 cancel result after a week switch is ignored`() {
        val gate = CompletableDeferred<Unit>()
        cooking.cancelGate = gate
        cooking.cancelResult = ApiResult.Failure(AppError.Server)
        val viewModel = planViewModel()
        viewModel.onEvent(WeekPlanEvent.GoToPlanWeek)
        viewModel.onEvent(WeekPlanEvent.RequestCancel("c-1"))
        viewModel.onEvent(WeekPlanEvent.CancelReasonChanged("krank"))
        viewModel.onEvent(WeekPlanEvent.ConfirmCancel)
        viewModel.onEvent(WeekPlanEvent.NextWeek)

        gate.complete(Unit)

        assertNull(viewModel.state.value.actionError)
        assertFalse(viewModel.state.value.isBusy)
        assertEquals(planWeek.plusWeeks(1), viewModel.state.value.weekStart)
    }
}
