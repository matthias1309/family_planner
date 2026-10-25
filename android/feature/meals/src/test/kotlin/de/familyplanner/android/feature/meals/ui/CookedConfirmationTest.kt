package de.familyplanner.android.feature.meals.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.meals.domain.cooking.ReviewDishUseCase
import de.familyplanner.android.feature.meals.domain.plan.CookCandidate
import de.familyplanner.android.feature.meals.domain.plan.DeleteDishUseCase
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.plan.DishUseCases
import de.familyplanner.android.feature.meals.domain.plan.EditorMode
import de.familyplanner.android.feature.meals.domain.plan.LoadDishEditorUseCase
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.domain.plan.SaveDishUseCase
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import de.familyplanner.android.feature.meals.domain.recipe.LoadRecipeUseCase
import de.familyplanner.android.feature.meals.fakes.FakeAttendanceRepository
import de.familyplanner.android.feature.meals.fakes.FakeCookingRepository
import de.familyplanner.android.feature.meals.fakes.FakeMealPlanRepository
import de.familyplanner.android.feature.meals.fakes.FakeRecipeRepository
import de.familyplanner.android.feature.meals.fakes.FakeReminderSync
import de.familyplanner.android.feature.meals.fakes.benDeadline
import de.familyplanner.android.feature.meals.fakes.childDish
import de.familyplanner.android.feature.meals.fakes.parentAnna
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.weekPlanOf
import de.familyplanner.android.feature.meals.ui.dish.DishEditArgs
import de.familyplanner.android.feature.meals.ui.dish.DishEditEvent
import de.familyplanner.android.feature.meals.ui.dish.DishEditScreen
import de.familyplanner.android.feature.meals.ui.dish.DishEditUiState
import de.familyplanner.android.feature.meals.ui.dish.DishEditViewModel
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanEvent
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanScreen
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Kochbestätigung in der App (REQ-MEAL-034). */
@RunWith(RobolectricTestRunner::class)
class CookedConfirmationTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val repository = FakeMealPlanRepository()
    private val attendance = FakeAttendanceRepository()
    private val cooking = FakeCookingRepository()
    private val wednesdayNoon = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val approved = childDish.copy(status = DishStatus.APPROVED)

    private fun editViewModel(dish: Dish = approved): DishEditViewModel {
        repository.weekResult = { ApiResult.Success(weekPlanOf(it, dish)) }
        return DishEditViewModel(
            DishEditArgs(dish.date, dish.id),
            InMemorySessionStore(parentSession),
            DishUseCases(
                LoadDishEditorUseCase(repository, attendance),
                SaveDishUseCase(repository, FakeReminderSync()),
                DeleteDishUseCase(repository, FakeReminderSync()),
                ReviewDishUseCase(cooking),
                LoadRecipeUseCase(FakeRecipeRepository()),
            ),
            wednesdayNoon,
        )
    }

    @Test
    fun `REQ-MEAL-034 confirming cooking closes the editor`() {
        val viewModel = editViewModel()
        assertTrue(viewModel.state.value.canConfirmCooking)

        viewModel.onEvent(DishEditEvent.RequestConfirmCooked)
        assertTrue(viewModel.state.value.showCookedConfirm)
        viewModel.onEvent(DishEditEvent.ConfirmCooked)

        assertEquals(listOf("d-2"), cooking.confirmedCooked)
        assertTrue(viewModel.state.value.isDone)
    }

    @Test
    fun `REQ-MEAL-034 conflict shows the server message`() {
        cooking.confirmCookedResult = ApiResult.Failure(AppError.Conflict("Schon bestätigt"))
        val viewModel = editViewModel()

        viewModel.onEvent(DishEditEvent.RequestConfirmCooked)
        viewModel.onEvent(DishEditEvent.ConfirmCooked)

        assertEquals(AppError.Conflict("Schon bestätigt"), viewModel.state.value.error)
        assertFalse(viewModel.state.value.isDone)
        assertFalse(viewModel.state.value.showCookedConfirm)
    }

    @Test
    fun `REQ-MEAL-034 nothing to confirm before the cooking day or after confirmation`() {
        val future = editViewModel(approved.copy(date = LocalDate.of(2026, 10, 25)))
        future.onEvent(DishEditEvent.RequestConfirmCooked)
        future.onEvent(DishEditEvent.ConfirmCooked)
        assertFalse(future.state.value.canConfirmCooking)
        assertFalse(future.state.value.showCookedConfirm)

        val cooked = editViewModel(approved.copy(cookedAt = Instant.EPOCH))
        cooked.onEvent(DishEditEvent.ConfirmCooked)
        assertTrue(cooking.confirmedCooked.isEmpty())
    }

    @Test
    fun `REQ-MEAL-034 cancelling the confirmation keeps the editor open`() {
        val viewModel = editViewModel()
        viewModel.onEvent(DishEditEvent.RequestConfirmCooked)

        viewModel.onEvent(DishEditEvent.CancelConfirmCooked)

        assertFalse(viewModel.state.value.showCookedConfirm)
        assertFalse(viewModel.state.value.isDone)
    }

    private val ben = CookCandidate("c-1", "Ben", setOf(approved.date), benDeadline)

    private fun editState(
        dish: Dish = approved,
        canConfirm: Boolean = true,
        showConfirm: Boolean = false,
    ) = DishEditUiState(
        dishId = dish.id,
        weekStart = planWeek,
        date = dish.date,
        name = dish.name,
        cookId = "c-1",
        parents = listOf(parentAnna),
        children = listOf(ben),
        existing = dish,
        mode = EditorMode.PARENT_EDIT,
        allowedDates = setOf(dish.date),
        isLoading = false,
        canConfirmCooking = canConfirm,
        showCookedConfirm = showConfirm,
    )

    private fun showEdit(
        state: DishEditUiState,
        events: MutableList<DishEditEvent> = mutableListOf(),
    ) = compose.setContent { FamilyPlannerTheme { DishEditScreen(state, { events += it }, {}, {}) } }

    @Test
    fun `REQ-MEAL-034 editor offers the confirm button only when allowed`() {
        val events = mutableListOf<DishEditEvent>()
        showEdit(editState(), events)

        compose.onNodeWithText("Als gekocht bestätigen").performScrollTo().performClick()
        compose.onNodeWithText("Ben bekommt dafür Kochpunkte.").assertExists()

        assertEquals(listOf<DishEditEvent>(DishEditEvent.RequestConfirmCooked), events)
    }

    @Test
    fun `REQ-MEAL-034 editor hides the button when not allowed`() {
        showEdit(editState(canConfirm = false))

        compose.onNodeWithText("Als gekocht bestätigen").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-034 editor shows when cooking was confirmed`() {
        showEdit(editState(approved.copy(cookedAt = Instant.parse("2026-10-21T10:00:00Z")), canConfirm = false))

        compose.onNodeWithText("Gekocht bestätigt am 21.10. 12:00").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-034 confirm dialog asks before booking`() {
        val events = mutableListOf<DishEditEvent>()
        showEdit(editState(showConfirm = true), events)

        compose.onNodeWithText("Gekocht bestätigen?").assertIsDisplayed()
        compose.onNodeWithText("Ben bekommt dafür Kochpunkte. Das lässt sich nicht zurücknehmen.").assertIsDisplayed()
        compose.onNodeWithText("Bestätigen").performClick()

        assertEquals(listOf<DishEditEvent>(DishEditEvent.ConfirmCooked), events)
    }

    private fun planState(
        dish: Dish,
        isParent: Boolean = true,
    ) = WeekPlanUiState(
        weekStart = planWeek,
        today = LocalDate.of(2026, 10, 21),
        week =
            WeekPlanSnapshot(
                planWeek,
                (0L..6L).map { offset ->
                    val date = planWeek.plusDays(offset)
                    PlannedDay(date, dish.takeIf { it.date == date }, "Ben".takeIf { dish.date == date }, cookIsChild = true)
                },
                Instant.parse("2026-10-21T10:00:00Z"),
            ),
        isParent = isParent,
        planWeek = planWeek,
        memberId = "p-1",
    )

    private fun showPlan(state: WeekPlanUiState) =
        compose.setContent { FamilyPlannerTheme { WeekPlanScreen(state, { _: WeekPlanEvent -> }, {}, {}, {}, {}) } }

    @Test
    fun `REQ-MEAL-034 plan marks cooked dishes for everyone`() {
        showPlan(planState(approved.copy(cookedAt = Instant.EPOCH), isParent = false))

        compose.onNodeWithText("Gekocht ✓").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-034 plan reminds parents of open confirmations`() {
        showPlan(planState(approved))

        compose.onNodeWithText("Kochen bestätigen?").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-034 plan shows no reminder to children or for future days`() {
        showPlan(planState(approved, isParent = false))
        compose.onNodeWithText("Kochen bestätigen?").assertDoesNotExist()
    }
}
