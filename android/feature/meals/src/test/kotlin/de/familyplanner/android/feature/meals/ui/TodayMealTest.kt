package de.familyplanner.android.feature.meals.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.plan.LoadWeekPlanUseCase
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import de.familyplanner.android.feature.meals.fakes.FakeMealPlanRepository
import de.familyplanner.android.feature.meals.fakes.InMemoryPlanCache
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.fakes.weekPlanOf
import de.familyplanner.android.feature.meals.ui.today.TodayMealCard
import de.familyplanner.android.feature.meals.ui.today.TodayMealUiState
import de.familyplanner.android.feature.meals.ui.today.TodayMealViewModel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TodayMealTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val repository = FakeMealPlanRepository()
    private val cache = InMemoryPlanCache()
    private val sessions = InMemorySessionStore(parentSession)

    private val monday = LocalDate.of(2026, 10, 26)
    private val wednesday = monday.plusDays(2)
    private val berlin = ZoneId.of("Europe/Berlin")
    private val onWednesday = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), berlin)
    private val onSunday = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), berlin)
    private val carbonara = Dish("d-9", wednesday, "p-1", "Lauch-Carbonara", listOf("Lauch"))

    private fun viewModel(clock: Clock) = TodayMealViewModel(LoadWeekPlanUseCase(repository, cache, sessions, clock), clock)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { FamilyPlannerTheme(content = content) }

    private fun planWith(vararg dishes: Dish) {
        repository.weekResult = { ApiResult.Success(weekPlanOf(it, *dishes)) }
    }

    @Test
    fun `REQ-TASK-002 today shows the dish of the current day`() {
        planWith(carbonara)

        val state = viewModel(onWednesday).state.value

        assertFalse(state.isLoading)
        assertEquals("Lauch-Carbonara", state.day?.dish?.name)
        assertEquals("Anna", state.day?.cookName)
    }

    @Test
    fun `REQ-TASK-002 sunday belongs to the week starting monday`() {
        planWith(carbonara.copy(date = onSundayDate()))

        val state = viewModel(onSunday).state.value

        assertEquals(listOf(monday), repository.requestedWeeks)
        assertEquals("Lauch-Carbonara", state.day?.dish?.name)
    }

    @Test
    fun `REQ-TASK-002 cached plan is shown when offline`() {
        val snapshot = WeekPlanSnapshot(monday, listOf(PlannedDay(wednesday, carbonara, "Anna")), Instant.parse("2026-10-27T08:00:00Z"))
        cache.stored[parentSession.memberId to monday] = snapshot
        repository.weekResult = { ApiResult.Failure(AppError.Network) }

        val state = viewModel(onWednesday).state.value

        assertFalse(state.unavailable)
        assertEquals("Lauch-Carbonara", state.day?.dish?.name)
    }

    @Test
    fun `REQ-TASK-002 unreachable plan without cache says unavailable`() {
        repository.weekResult = { ApiResult.Failure(AppError.Network) }

        val state = viewModel(onWednesday).state.value

        assertTrue(state.unavailable)
        assertNull(state.day)
    }

    @Test
    fun `REQ-TASK-002 card shows dish and cook`() {
        show { TodayMealCard(TodayMealUiState(false, PlannedDay(wednesday, carbonara, "Ben")), {}) }

        compose.onNodeWithText("Heute gibt es").assertIsDisplayed()
        compose.onNodeWithText("Lauch-Carbonara").assertIsDisplayed()
        compose.onNodeWithText("Es kocht: Ben").assertIsDisplayed()
        compose.onNodeWithText("Noch nicht freigegeben.").assertDoesNotExist()
    }

    @Test
    fun `REQ-TASK-002 today without dish shows nothing planned`() {
        show { TodayMealCard(TodayMealUiState(false, PlannedDay(wednesday, null, null)), {}) }

        compose.onNodeWithText("Heute ist noch nichts geplant.").assertIsDisplayed()
    }

    @Test
    fun `REQ-TASK-002 rejected child dish counts as nothing planned`() {
        val rejected = carbonara.copy(status = DishStatus.REJECTED)
        show { TodayMealCard(TodayMealUiState(false, PlannedDay(wednesday, rejected, "Ben")), {}) }

        compose.onNodeWithText("Heute ist noch nichts geplant.").assertIsDisplayed()
        compose.onNodeWithText("Lauch-Carbonara").assertDoesNotExist()
    }

    @Test
    fun `REQ-TASK-002 submitted dish is marked as not yet approved`() {
        val submitted = carbonara.copy(status = DishStatus.SUBMITTED)
        show { TodayMealCard(TodayMealUiState(false, PlannedDay(wednesday, submitted, "Ben")), {}) }

        compose.onNodeWithText("Lauch-Carbonara").assertIsDisplayed()
        compose.onNodeWithText("Noch nicht freigegeben.").assertIsDisplayed()
    }

    @Test
    fun `REQ-TASK-002 unavailable plan is announced and tap opens the plan`() {
        var opened = false
        show { TodayMealCard(TodayMealUiState(false, unavailable = true), { opened = true }) }

        compose.onNodeWithText("Essensplan gerade nicht erreichbar.").assertIsDisplayed()
        compose.onNodeWithText("Heute gibt es").performClick()
        assertTrue(opened)
    }

    private fun onSundayDate() = LocalDate.of(2026, 11, 1)
}
