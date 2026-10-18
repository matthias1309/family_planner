package de.familyplanner.android.feature.meals.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.PlanMember
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import de.familyplanner.android.feature.meals.fakes.parentAnna
import de.familyplanner.android.feature.meals.fakes.parentTom
import de.familyplanner.android.feature.meals.fakes.someDish
import de.familyplanner.android.feature.meals.fakes.thisWeek
import de.familyplanner.android.feature.meals.ui.dish.DishEditEvent
import de.familyplanner.android.feature.meals.ui.dish.DishEditScreen
import de.familyplanner.android.feature.meals.ui.dish.DishEditUiState
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanEvent
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanScreen
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PlanScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { FamilyPlannerTheme(content = content) }

    private val today = LocalDate.of(2026, 10, 17)
    private val syncedAt = Instant.parse("2026-10-18T10:00:00Z")
    private val pizza = Dish("d-2", thisWeek.plusDays(2), "p-2", "Pizza", emptyList())

    private fun snapshot(vararg dishes: Pair<Dish, String>) =
        WeekPlanSnapshot(
            thisWeek,
            (0L..6L).map { offset ->
                val date = thisWeek.plusDays(offset)
                val match = dishes.firstOrNull { it.first.date == date }
                PlannedDay(date, match?.first, match?.second)
            },
            syncedAt,
        )

    private fun planState(
        week: WeekPlanSnapshot? = snapshot(someDish to "Anna", pizza to "Tom"),
        isParent: Boolean = false,
        isOffline: Boolean = false,
        error: AppError? = null,
        cacheReason: AppError? = null,
    ) = WeekPlanUiState(thisWeek, today, week, error = error, isOffline = isOffline, cacheReason = cacheReason, isParent = isParent)

    private fun showPlan(
        state: WeekPlanUiState,
        onEvent: (WeekPlanEvent) -> Unit = {},
        onOpenDay: (LocalDate, String?) -> Unit = { _, _ -> },
    ) = show { WeekPlanScreen(state, onEvent, onOpenDay, {}) }

    @Test
    fun `REQ-MEAL-001 day with dish shows name and cook`() {
        showPlan(planState())

        compose.onNodeWithText("Mo 12.10.").assertIsDisplayed()
        compose.onNodeWithText("Spaghetti").assertIsDisplayed()
        compose.onNodeWithText("Anna kocht").assertIsDisplayed()
        compose.onNodeWithText("Nudeln, Hackfleisch").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-001 open day shows noch offen`() {
        showPlan(planState())

        compose.onNodeWithText("Di 13.10.").assertIsDisplayed()
        compose.onAllNodesWithText("noch offen").onFirst().assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-001 today is marked`() {
        showPlan(planState())

        // Samstag liegt im kleinen Testfenster unten.
        compose.onNodeWithText("Heute").assertExists()
    }

    @Test
    fun `REQ-MEAL-001 week title and navigation`() {
        val events = mutableListOf<WeekPlanEvent>()
        showPlan(planState(), onEvent = { events += it })

        compose.onNodeWithText("Woche 12.10.–18.10.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Nächste Woche").performClick()
        compose.onNodeWithContentDescription("Vorige Woche").performClick()

        assertEquals(listOf<WeekPlanEvent>(WeekPlanEvent.NextWeek, WeekPlanEvent.PreviousWeek), events)
    }

    @Test
    fun `REQ-MEAL-002 offline hint shows sync time in Berlin time`() {
        showPlan(planState(isOffline = true))

        compose.onNodeWithText("Offline – Stand: So 18.10., 12:00").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-002 server problem hint does not claim offline`() {
        showPlan(planState(isOffline = true, cacheReason = AppError.Server))

        compose.onNodeWithText("Server gestört – gespeicherter Stand: So 18.10., 12:00").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-032 parent taps open day to add dish`() {
        val opened = mutableListOf<Pair<LocalDate, String?>>()
        showPlan(planState(isParent = true), onOpenDay = { date, id -> opened += date to id })

        compose.onNodeWithText("Di 13.10.").performClick()

        assertEquals(listOf<Pair<LocalDate, String?>>(thisWeek.plusDays(1) to null), opened)
    }

    @Test
    fun `REQ-MEAL-032 parent taps planned day to edit`() {
        val opened = mutableListOf<Pair<LocalDate, String?>>()
        showPlan(planState(isParent = true), onOpenDay = { date, id -> opened += date to id })

        compose.onNodeWithText("Spaghetti").performClick()

        assertEquals(listOf<Pair<LocalDate, String?>>(thisWeek to "d-1"), opened)
    }

    @Test
    fun `REQ-MEAL-032 child cannot open the editor`() {
        val opened = mutableListOf<Pair<LocalDate, String?>>()
        showPlan(planState(isParent = false), onOpenDay = { date, id -> opened += date to id })

        compose.onNodeWithText("Spaghetti").performClick()

        assertTrue(opened.isEmpty())
        compose.onNodeWithText("+ Gericht").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-032 offline parent sees edit hint`() {
        val opened = mutableListOf<Pair<LocalDate, String?>>()
        showPlan(planState(isParent = true, isOffline = true), onOpenDay = { date, id -> opened += date to id })

        compose.onNodeWithText("Bearbeiten ist nur im Heim-WLAN möglich.").assertIsDisplayed()
        compose.onNodeWithText("Spaghetti").performClick()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `network error is shown in German`() {
        showPlan(planState(week = null, error = AppError.Network))

        compose.onNodeWithText("Server nicht erreichbar. Bist du im Heim-WLAN?").assertIsDisplayed()
    }

    private fun editState(
        dishId: String? = null,
        takenDates: Set<LocalDate> = emptySet(),
        error: AppError? = null,
    ) = DishEditUiState(
        dishId = dishId,
        weekStart = thisWeek,
        date = thisWeek.plusDays(1),
        name = "Curry",
        ingredientsText = "Reis",
        cookId = "p-1",
        parents = listOf<PlanMember>(parentAnna, parentTom),
        takenDates = takenDates,
        isLoading = false,
        error = error,
    )

    private fun showEdit(
        state: DishEditUiState,
        onEvent: (DishEditEvent) -> Unit = {},
    ) = show { DishEditScreen(state, onEvent, {}) }

    @Test
    fun `REQ-MEAL-032 editor shows fields and save`() {
        val events = mutableListOf<DishEditEvent>()
        showEdit(editState(), onEvent = { events += it })

        compose.onNodeWithText("Gericht planen").assertIsDisplayed()
        compose.onNodeWithText("Curry").assertIsDisplayed()
        compose.onNodeWithText("Anna").assertIsDisplayed()
        compose
            .onNodeWithText("Speichern")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(listOf<DishEditEvent>(DishEditEvent.Save), events)
    }

    @Test
    fun `REQ-MEAL-032 taken day chip is disabled`() {
        showEdit(editState(takenDates = setOf(thisWeek)))

        compose.onNodeWithContentDescription("Mo 12.10. (belegt)").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mi 14.10.").assertIsEnabled()
    }

    @Test
    fun `REQ-MEAL-032 delete is offered only for existing dishes`() {
        showEdit(editState(dishId = "d-1"))
        compose.onNodeWithText("Gericht ändern").assertIsDisplayed()
        compose.onNodeWithText("Löschen").assertExists()
    }

    @Test
    fun `new dish has no delete button`() {
        showEdit(editState())

        compose.onNodeWithText("Löschen").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-032 conflict message is shown`() {
        showEdit(editState(error = AppError.Conflict("An diesem Tag ist schon ein Gericht geplant")))

        compose.onNodeWithText("An diesem Tag ist schon ein Gericht geplant").assertExists()
    }
}
