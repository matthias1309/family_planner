package de.familyplanner.android.feature.meals.ui

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.meals.domain.cooking.CancelCookingUseCase
import de.familyplanner.android.feature.meals.domain.cooking.CookingUseCases
import de.familyplanner.android.feature.meals.domain.cooking.LoadWeekStatusUseCase
import de.familyplanner.android.feature.meals.domain.cooking.ReviewDishUseCase
import de.familyplanner.android.feature.meals.domain.cooking.WithdrawCancellationUseCase
import de.familyplanner.android.feature.meals.domain.plan.DeleteDishUseCase
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishUseCases
import de.familyplanner.android.feature.meals.domain.plan.LoadDishEditorUseCase
import de.familyplanner.android.feature.meals.domain.plan.LoadWeekPlanUseCase
import de.familyplanner.android.feature.meals.domain.plan.SaveDishUseCase
import de.familyplanner.android.feature.meals.fakes.FakeAttendanceRepository
import de.familyplanner.android.feature.meals.fakes.FakeCookingRepository
import de.familyplanner.android.feature.meals.fakes.FakeMealPlanRepository
import de.familyplanner.android.feature.meals.fakes.InMemoryPlanCache
import de.familyplanner.android.feature.meals.fakes.childSession
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.fakes.someDish
import de.familyplanner.android.feature.meals.fakes.thisWeek
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

class PlanViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeMealPlanRepository()
    private val cache = InMemoryPlanCache()
    private var sessions = InMemorySessionStore(parentSession)

    // Samstag, 17.10.2026, 12:00 Berlin
    private val saturday = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Berlin"))

    private val attendance = FakeAttendanceRepository()
    private val cooking = FakeCookingRepository()

    private fun planViewModel(clock: Clock = saturday) =
        WeekPlanViewModel(
            sessions,
            LoadWeekPlanUseCase(repository, cache, sessions, clock),
            CookingUseCases(
                LoadWeekStatusUseCase(cooking, repository),
                CancelCookingUseCase(cooking),
                WithdrawCancellationUseCase(cooking),
            ),
            clock,
        )

    private fun editViewModel(
        date: LocalDate = thisWeek.plusDays(1),
        dishId: String? = null,
    ) = DishEditViewModel(
        DishEditArgs(date, dishId),
        sessions,
        DishUseCases(
            LoadDishEditorUseCase(repository, attendance),
            SaveDishUseCase(repository),
            DeleteDishUseCase(repository),
            ReviewDishUseCase(cooking),
        ),
    )

    @Test
    fun `REQ-MEAL-001 starts at this week and prefetches next week`() {
        val viewModel = planViewModel()

        assertEquals(thisWeek, viewModel.state.value.weekStart)
        assertEquals(LocalDate.of(2026, 10, 17), viewModel.state.value.today)
        assertEquals(
            thisWeek,
            viewModel.state.value.week
                ?.weekStart,
        )
        assertEquals(listOf(thisWeek, thisWeek.plusWeeks(1)), repository.requestedWeeks)
        assertNotNull(cache.stored["p-1" to thisWeek.plusWeeks(1)])
    }

    @Test
    fun `REQ-MEAL-001 on sunday next week is one step away`() {
        val sunday = Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneId.of("Europe/Berlin"))
        val viewModel = planViewModel(sunday)

        assertEquals(thisWeek, viewModel.state.value.weekStart)
        viewModel.onEvent(WeekPlanEvent.NextWeek)

        assertEquals(LocalDate.of(2026, 10, 19), viewModel.state.value.weekStart)
    }

    @Test
    fun `REQ-MEAL-001 next and previous week reload`() {
        val viewModel = planViewModel()

        viewModel.onEvent(WeekPlanEvent.NextWeek)
        assertEquals(
            thisWeek.plusWeeks(1),
            viewModel.state.value.week
                ?.weekStart,
        )
        viewModel.onEvent(WeekPlanEvent.PreviousWeek)
        viewModel.onEvent(WeekPlanEvent.PreviousWeek)

        assertEquals(thisWeek.minusWeeks(1), viewModel.state.value.weekStart)
        assertEquals(
            thisWeek.minusWeeks(1),
            viewModel.state.value.week
                ?.weekStart,
        )
    }

    @Test
    fun `late response of previous week is ignored`() {
        val gate = CompletableDeferred<Unit>()
        repository.weekGates[thisWeek] = gate
        val viewModel = planViewModel()

        viewModel.onEvent(WeekPlanEvent.NextWeek)
        gate.complete(Unit)

        assertEquals(thisWeek.plusWeeks(1), viewModel.state.value.weekStart)
        assertEquals(
            thisWeek.plusWeeks(1),
            viewModel.state.value.week
                ?.weekStart,
        )
    }

    @Test
    fun `REQ-MEAL-002 offline with cache shows cached week as offline`() {
        val viewModel = planViewModel()
        repository.weekResult = { ApiResult.Failure(AppError.Network) }

        viewModel.onEvent(WeekPlanEvent.Refresh)

        val state = viewModel.state.value
        assertTrue(state.isOffline)
        assertNull(state.error)
        assertEquals(thisWeek, state.week?.weekStart)
        assertFalse(state.canEdit)
    }

    @Test
    fun `REQ-MEAL-002 server error with cache keeps the reason`() {
        val viewModel = planViewModel()
        repository.weekResult = { ApiResult.Failure(AppError.Server) }

        viewModel.onEvent(WeekPlanEvent.Refresh)

        assertEquals(AppError.Server, viewModel.state.value.cacheReason)
        assertTrue(viewModel.state.value.isOffline)
    }

    @Test
    fun `today is updated on every load`() {
        var instant = Instant.parse("2026-10-17T10:00:00Z")
        val moving =
            object : Clock() {
                override fun getZone() = ZoneId.of("Europe/Berlin")

                override fun withZone(zone: ZoneId?) = this

                override fun instant() = instant
            }
        val viewModel = planViewModel(moving)
        assertEquals(LocalDate.of(2026, 10, 17), viewModel.state.value.today)

        instant = Instant.parse("2026-10-17T22:30:00Z")
        viewModel.onEvent(WeekPlanEvent.Refresh)

        assertEquals(LocalDate.of(2026, 10, 18), viewModel.state.value.today)
    }

    @Test
    fun `REQ-MEAL-002 offline without cache shows network error`() {
        repository.weekResult = { ApiResult.Failure(AppError.Network) }

        val state = planViewModel().state.value

        assertEquals(AppError.Network, state.error)
        assertNull(state.week)
        assertFalse(state.isOffline)
    }

    @Test
    fun `REQ-MEAL-002 refresh after coming back online clears offline`() {
        val viewModel = planViewModel()
        repository.weekResult = { ApiResult.Failure(AppError.Network) }
        viewModel.onEvent(WeekPlanEvent.Refresh)
        repository.weekResult = { ApiResult.Success(weekPlanOf(it)) }

        viewModel.onEvent(WeekPlanEvent.Refresh)

        assertFalse(viewModel.state.value.isOffline)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `REQ-MEAL-032 only online parents can edit`() {
        assertTrue(planViewModel().state.value.canEdit)

        sessions = InMemorySessionStore(childSession)
        assertFalse(planViewModel().state.value.canEdit)
    }

    @Test
    fun `REQ-MEAL-032 new dish preselects own parent as cook and the tapped day`() {
        val state = editViewModel(thisWeek.plusDays(1)).state.value

        assertEquals("p-1", state.cookId)
        assertEquals(thisWeek.plusDays(1), state.date)
        assertEquals(listOf("p-1", "p-2"), state.parents.map { it.id })
        assertFalse(state.isLoading)
        assertTrue(state.isNew)
    }

    @Test
    fun `REQ-MEAL-032 existing dish fills the form`() {
        val state = editViewModel(thisWeek, "d-1").state.value

        assertEquals("Spaghetti", state.name)
        assertEquals("Nudeln\nHackfleisch", state.ingredientsText)
        assertEquals("p-1", state.cookId)
        assertEquals(thisWeek, state.date)
    }

    @Test
    fun `REQ-MEAL-032 days with other dishes are taken`() {
        val wednesday = Dish("d-2", thisWeek.plusDays(2), "p-2", "Pizza", emptyList())
        repository.weekResult = { ApiResult.Success(weekPlanOf(it, someDish, wednesday)) }

        val state = editViewModel(wednesday.date, "d-2").state.value

        assertEquals(setOf(thisWeek), state.takenDates)
    }

    @Test
    fun `taken day cannot be selected`() {
        val viewModel = editViewModel(thisWeek.plusDays(1))

        viewModel.onEvent(DishEditEvent.DateSelected(thisWeek))
        assertEquals(thisWeek.plusDays(1), viewModel.state.value.date)
        viewModel.onEvent(DishEditEvent.DateSelected(thisWeek.plusDays(3)))
        assertEquals(thisWeek.plusDays(3), viewModel.state.value.date)
    }

    @Test
    fun `REQ-MEAL-032 saving new dish creates it and finishes`() {
        val viewModel = editViewModel(thisWeek.plusDays(1))
        viewModel.onEvent(DishEditEvent.NameChanged(" Curry "))
        viewModel.onEvent(DishEditEvent.IngredientsChanged("Reis\n\n Erbsen"))
        viewModel.onEvent(DishEditEvent.CookSelected("p-2"))

        viewModel.onEvent(DishEditEvent.Save)

        val draft = repository.created.single()
        assertEquals("Curry", draft.name)
        assertEquals(listOf("Reis", "Erbsen"), draft.ingredients)
        assertEquals("p-2", draft.cookId)
        assertTrue(viewModel.state.value.isDone)
    }

    @Test
    fun `REQ-MEAL-032 saving existing dish updates it and finishes`() {
        val viewModel = editViewModel(thisWeek, "d-1")
        viewModel.onEvent(DishEditEvent.DateSelected(thisWeek.plusDays(4)))

        viewModel.onEvent(DishEditEvent.Save)

        val (id, draft) = repository.updated.single()
        assertEquals("d-1", id)
        assertEquals(thisWeek.plusDays(4), draft.date)
        assertTrue(viewModel.state.value.isDone)
    }

    @Test
    fun `REQ-MEAL-032 taken day conflict keeps form with server message`() {
        repository.createResult = ApiResult.Failure(AppError.Conflict("An diesem Tag ist schon ein Gericht geplant"))
        val viewModel = editViewModel()
        viewModel.onEvent(DishEditEvent.NameChanged("Curry"))

        viewModel.onEvent(DishEditEvent.Save)

        val state = viewModel.state.value
        assertEquals(AppError.Conflict("An diesem Tag ist schon ein Gericht geplant"), state.error)
        assertFalse(state.isDone)
        assertFalse(state.isBusy)
        assertEquals("Curry", state.name)
    }

    @Test
    fun `REQ-MEAL-032 invalid ingredients show validation message`() {
        val viewModel = editViewModel()
        viewModel.onEvent(DishEditEvent.NameChanged("Curry"))
        viewModel.onEvent(DishEditEvent.IngredientsChanged((1..31).joinToString("\n") { "Z$it" }))

        viewModel.onEvent(DishEditEvent.Save)

        assertEquals(AppError.Validation("Höchstens 30 Zutaten je Gericht."), viewModel.state.value.error)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `date taken by another device cannot be saved`() {
        // Das Gericht d-1 liegt schon auf dem Montag: der belegte Tag wird nicht vorausgewählt und lässt sich nicht wählen.
        val viewModel = editViewModel(thisWeek)
        viewModel.onEvent(DishEditEvent.NameChanged("Curry"))
        assertEquals(thisWeek.plusDays(1), viewModel.state.value.date)

        viewModel.onEvent(DishEditEvent.DateSelected(thisWeek))

        assertEquals(thisWeek.plusDays(1), viewModel.state.value.date)
        assertFalse(thisWeek in viewModel.state.value.allowedDates)
    }

    @Test
    fun `name is cut at the limit and editing clears the error`() {
        val viewModel = editViewModel()

        viewModel.onEvent(DishEditEvent.NameChanged("a".repeat(70)))

        assertEquals(60, viewModel.state.value.name.length)
    }

    @Test
    fun `REQ-MEAL-032 forbidden save shows parents only`() {
        repository.createResult = ApiResult.Failure(AppError.Forbidden)
        val viewModel = editViewModel()
        viewModel.onEvent(DishEditEvent.NameChanged("Curry"))

        viewModel.onEvent(DishEditEvent.Save)

        assertEquals(AppError.Forbidden, viewModel.state.value.error)
    }

    @Test
    fun `REQ-MEAL-032 deleting requires confirmation`() {
        val viewModel = editViewModel(thisWeek, "d-1")

        viewModel.onEvent(DishEditEvent.RequestDelete)
        assertTrue(viewModel.state.value.showDeleteConfirm)
        assertTrue(repository.deleted.isEmpty())
        viewModel.onEvent(DishEditEvent.CancelDelete)
        assertFalse(viewModel.state.value.showDeleteConfirm)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `REQ-MEAL-032 deleted dish finishes`() {
        val viewModel = editViewModel(thisWeek, "d-1")

        viewModel.onEvent(DishEditEvent.RequestDelete)
        viewModel.onEvent(DishEditEvent.ConfirmDelete)

        assertEquals(listOf("d-1"), repository.deleted)
        assertTrue(viewModel.state.value.isDone)
    }

    @Test
    fun `deleting an already deleted dish finishes`() {
        repository.deleteResult = ApiResult.Failure(AppError.NotFound)
        val viewModel = editViewModel(thisWeek, "d-1")

        viewModel.onEvent(DishEditEvent.ConfirmDelete)

        assertTrue(viewModel.state.value.isDone)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `failed delete shows the error`() {
        repository.deleteResult = ApiResult.Failure(AppError.Network)
        val viewModel = editViewModel(thisWeek, "d-1")

        viewModel.onEvent(DishEditEvent.ConfirmDelete)

        assertEquals(AppError.Network, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isDone)
    }

    @Test
    fun `unknown dish id shows not found`() {
        val state = editViewModel(thisWeek, "gone").state.value

        assertEquals(AppError.NotFound, state.loadError)
        assertFalse(state.canSave)
    }

    @Test
    fun `cook who is no longer a parent must be chosen again`() {
        repository.weekResult = { ApiResult.Success(weekPlanOf(it, someDish.copy(cookId = "gone"))) }

        val state = editViewModel(thisWeek, "d-1").state.value

        assertNull(state.cookId)
        assertFalse(state.canSave)
    }

    @Test
    fun `double save sends once`() {
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate
        val viewModel = editViewModel()
        viewModel.onEvent(DishEditEvent.NameChanged("Curry"))

        viewModel.onEvent(DishEditEvent.Save)
        viewModel.onEvent(DishEditEvent.Save)
        gate.complete(Unit)

        assertEquals(1, repository.created.size)
        assertTrue(viewModel.state.value.isDone)
    }

    @Test
    fun `retry after failed load loads again`() {
        repository.membersResult = ApiResult.Failure(AppError.Network)
        val viewModel = editViewModel()
        assertEquals(AppError.Network, viewModel.state.value.loadError)
        repository.membersResult = ApiResult.Success(listOf(de.familyplanner.android.feature.meals.fakes.parentAnna))

        viewModel.onEvent(DishEditEvent.Retry)

        assertNull(viewModel.state.value.loadError)
        assertIs<String>(viewModel.state.value.cookId)
    }
}
