package de.familyplanner.android.feature.meals.ui

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingListCache
import de.familyplanner.android.feature.meals.fakes.FakeShoppingRepository
import de.familyplanner.android.feature.meals.fakes.childSession
import de.familyplanner.android.feature.meals.fakes.freeEntry
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.shoppingEntry
import de.familyplanner.android.feature.meals.fakes.shoppingListOf
import de.familyplanner.android.feature.meals.fakes.shoppingUseCases
import de.familyplanner.android.feature.meals.ui.shopping.ShoppingListArgs
import de.familyplanner.android.feature.meals.ui.shopping.ShoppingListEvent
import de.familyplanner.android.feature.meals.ui.shopping.ShoppingListViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShoppingViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeShoppingRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val nextWeek = planWeek.plusWeeks(1)

    private val sessions = InMemorySessionStore(parentSession)
    private val cache = ShoppingListCache(sessions)

    private fun viewModel(
        session: Session = parentSession,
        args: ShoppingListArgs = ShoppingListArgs(),
    ): ShoppingListViewModel {
        sessions.setCurrent(session)
        return ShoppingListViewModel(args, sessions, shoppingUseCases(repository, cache), clock)
    }

    // ---- Zuletzt geladene Liste (ADR-0018) ----

    @Test
    fun `REQ-MEAL-040 reopened list shows the last loaded list before the server answers`() {
        viewModel()
        repository.listGates[planWeek] = CompletableDeferred()

        val state = viewModel().state.value

        assertEquals(1, state.sections.open.size)
        assertTrue(state.isLoading)
    }

    @Test
    fun `REQ-MEAL-040 fresh list replaces the last loaded list`() {
        viewModel()
        val gate = CompletableDeferred<Unit>()
        repository.listGates[planWeek] = gate
        repository.listResult = { ApiResult.Success(shoppingListOf(shoppingEntry(), freeEntry, weekStart = it)) }
        val viewModel = viewModel()

        gate.complete(Unit)

        assertEquals(2, viewModel.state.value.sections.open.size)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `REQ-MEAL-040 failed reload keeps the last loaded list and shows the error`() {
        viewModel()
        repository.listResult = { ApiResult.Failure(AppError.Network) }

        val state = viewModel().state.value

        assertEquals(1, state.sections.open.size)
        assertEquals(AppError.Network, state.error)
        assertFalse(state.canEdit)
    }

    @Test
    fun `REQ-MEAL-040 rejected reload does not show the last loaded list`() {
        viewModel()
        repository.listResult = { ApiResult.Failure(AppError.Forbidden) }

        val state = viewModel().state.value

        assertNull(state.list)
        assertEquals(AppError.Forbidden, state.error)
    }

    @Test
    fun `REQ-MEAL-041 successful reload makes the kept list editable again`() {
        viewModel()
        repository.listResult = { ApiResult.Failure(AppError.Network) }
        val viewModel = viewModel()
        repository.listResult = { ApiResult.Success(shoppingListOf(shoppingEntry(), weekStart = it)) }

        viewModel.onEvent(ShoppingListEvent.Refresh)

        assertTrue(viewModel.state.value.canEdit)
    }

    @Test
    fun `REQ-MEAL-041 checked entry is kept for the next opening`() {
        viewModel().onEvent(ShoppingListEvent.SetChecked("k-1", true))
        repository.listGates[planWeek] = CompletableDeferred()

        val state = viewModel().state.value

        assertEquals(1, state.sections.done.size)
    }

    @Test
    fun `REQ-MEAL-041 deleted item is gone at the next opening`() {
        repository.listResult = { ApiResult.Success(shoppingListOf(shoppingEntry(), freeEntry, weekStart = it)) }
        val first = viewModel()
        repository.listGates[planWeek] = CompletableDeferred()

        first.onEvent(ShoppingListEvent.DeleteItem("i-1"))

        assertEquals(
            listOf("k-1"),
            viewModel()
                .state.value.sections.open
                .map { it.key },
        )
    }

    @Test
    fun `REQ-MEAL-040 another member does not see the last loaded list`() {
        viewModel()
        repository.listGates[planWeek] = CompletableDeferred()

        assertNull(viewModel(session = childSession).state.value.list)
    }

    @Test
    fun `REQ-MEAL-040 opens plan week by default`() {
        val viewModel = viewModel()

        assertEquals(planWeek, viewModel.state.value.weekStart)
        assertEquals(listOf(planWeek), repository.requestedWeeks)
        assertEquals(1, viewModel.state.value.sections.open.size)
    }

    @Test
    fun `REQ-MEAL-040 opens requested week`() {
        val viewModel = viewModel(args = ShoppingListArgs(nextWeek))

        assertEquals(nextWeek, viewModel.state.value.weekStart)
        assertEquals(listOf(nextWeek), repository.requestedWeeks)
    }

    @Test
    fun `REQ-MEAL-040 late answer of previous week is discarded`() {
        val gate = CompletableDeferred<Unit>()
        repository.listGates[planWeek] = gate
        repository.listResult = { ApiResult.Success(shoppingListOf(shoppingEntry("w-$it"), weekStart = it)) }
        val viewModel = viewModel()

        viewModel.onEvent(ShoppingListEvent.NextWeek)
        gate.complete(Unit)

        assertEquals(nextWeek, viewModel.state.value.weekStart)
        assertEquals(
            nextWeek,
            viewModel.state.value.list
                ?.weekStart,
        )
    }

    @Test
    fun `REQ-MEAL-040 offline shows network error`() {
        repository.listResult = { ApiResult.Failure(AppError.Network) }

        val viewModel = viewModel()

        assertEquals(AppError.Network, viewModel.state.value.error)
        assertNull(viewModel.state.value.list)
    }

    @Test
    fun `REQ-MEAL-041 add item clears field and takes list`() {
        val viewModel = viewModel()
        repository.addResult = ApiResult.Success(shoppingListOf(shoppingEntry(), freeEntry))

        viewModel.onEvent(ShoppingListEvent.NewItemChanged("2 kg Kartoffeln"))
        viewModel.onEvent(ShoppingListEvent.AddItem)

        assertEquals(listOf(planWeek to Ingredient("Kartoffeln", 2.0, "kg")), repository.added)
        assertEquals("", viewModel.state.value.newItem)
        assertEquals(
            2,
            viewModel.state.value.list
                ?.entries
                ?.size,
        )
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `REQ-MEAL-041 invalid item sends no request and keeps text`() {
        val viewModel = viewModel()

        viewModel.onEvent(ShoppingListEvent.NewItemChanged("   "))
        viewModel.onEvent(ShoppingListEvent.AddItem)

        assertTrue(repository.added.isEmpty())
        assertEquals("   ", viewModel.state.value.newItem)
        assertEquals(AppError.Validation("Bitte einen Eintrag eingeben."), viewModel.state.value.actionError)
    }

    @Test
    fun `REQ-MEAL-041 failed add keeps text and shows error`() {
        val viewModel = viewModel()
        repository.addResult = ApiResult.Failure(AppError.Validation("Höchstens 50 freie Einträge"))

        viewModel.onEvent(ShoppingListEvent.NewItemChanged("Brot"))
        viewModel.onEvent(ShoppingListEvent.AddItem)

        assertEquals("Brot", viewModel.state.value.newItem)
        assertEquals(AppError.Validation("Höchstens 50 freie Einträge"), viewModel.state.value.actionError)
    }

    @Test
    fun `REQ-MEAL-041 input is limited to 80 characters`() {
        val viewModel = viewModel()

        viewModel.onEvent(ShoppingListEvent.NewItemChanged("x".repeat(100)))

        assertEquals(80, viewModel.state.value.newItem.length)
    }

    @Test
    fun `REQ-MEAL-041 check sends key and flag and takes list`() {
        val viewModel = viewModel()

        viewModel.onEvent(ShoppingListEvent.SetChecked("k-1", true))

        assertEquals(listOf(Triple(planWeek, "k-1", true)), repository.checks)
        assertEquals(1, viewModel.state.value.sections.done.size)
    }

    @Test
    fun `REQ-MEAL-041 write cancels pending refresh so its answer is kept`() {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>()
        repository.listGates[planWeek] = gate

        viewModel.onEvent(ShoppingListEvent.Refresh)
        viewModel.onEvent(ShoppingListEvent.SetChecked("k-1", true))
        gate.complete(Unit)

        assertEquals(1, viewModel.state.value.sections.done.size)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `REQ-MEAL-041 unknown key reloads list`() {
        val viewModel = viewModel()
        repository.checkResult = ApiResult.Failure(AppError.NotFound)

        viewModel.onEvent(ShoppingListEvent.SetChecked("gone", true))

        assertEquals(AppError.NotFound, viewModel.state.value.actionError)
        assertEquals(listOf(planWeek, planWeek), repository.requestedWeeks)
    }

    @Test
    fun `REQ-MEAL-041 delete reloads list`() {
        val viewModel = viewModel()

        viewModel.onEvent(ShoppingListEvent.DeleteItem("i-1"))

        assertEquals(listOf(planWeek to "i-1"), repository.deleted)
        assertEquals(listOf(planWeek, planWeek), repository.requestedWeeks)
    }

    @Test
    fun `REQ-MEAL-041 failed delete shows error`() {
        val viewModel = viewModel()
        repository.deleteResult = ApiResult.Failure(AppError.Forbidden)

        viewModel.onEvent(ShoppingListEvent.DeleteItem("i-1"))

        assertEquals(AppError.Forbidden, viewModel.state.value.actionError)
        assertEquals(1, repository.requestedWeeks.size)
    }

    @Test
    fun `REQ-MEAL-041 second write while busy sends no request`() {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>()
        repository.checkGate = gate

        viewModel.onEvent(ShoppingListEvent.SetChecked("k-1", true))
        viewModel.onEvent(ShoppingListEvent.SetChecked("k-1", false))
        gate.complete(Unit)

        assertEquals(1, repository.checks.size)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `REQ-MEAL-041 child cannot write`() {
        val viewModel = viewModel(childSession)

        viewModel.onEvent(ShoppingListEvent.NewItemChanged("Brot"))
        viewModel.onEvent(ShoppingListEvent.AddItem)
        viewModel.onEvent(ShoppingListEvent.SetChecked("k-1", true))
        viewModel.onEvent(ShoppingListEvent.DeleteItem("i-1"))

        assertFalse(viewModel.state.value.canEdit)
        assertTrue(repository.added.isEmpty() && repository.checks.isEmpty() && repository.deleted.isEmpty())
    }

    @Test
    fun `REQ-MEAL-040 week change resets input and respects limits`() {
        val viewModel = viewModel(args = ShoppingListArgs(LocalDate.of(2000, 1, 3)))

        viewModel.onEvent(ShoppingListEvent.NewItemChanged("Brot"))
        viewModel.onEvent(ShoppingListEvent.PreviousWeek)

        assertFalse(viewModel.state.value.canGoPrevious)
        assertEquals(LocalDate.of(2000, 1, 3), viewModel.state.value.weekStart)
    }
}
