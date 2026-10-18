package de.familyplanner.android.feature.meals.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.meals.domain.cooking.CookOverview
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.cooking.StatusOverview
import de.familyplanner.android.feature.meals.domain.plan.CookCandidate
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.plan.EditorMode
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import de.familyplanner.android.feature.meals.fakes.benDeadline
import de.familyplanner.android.feature.meals.fakes.childDish
import de.familyplanner.android.feature.meals.fakes.cookStatus
import de.familyplanner.android.feature.meals.fakes.parentAnna
import de.familyplanner.android.feature.meals.fakes.parentTom
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.ui.dish.DishEditArgs
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
class CookingScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { FamilyPlannerTheme(content = content) }

    private val tuesday = planWeek.plusDays(1)
    private val wednesday = planWeek.plusDays(2)
    private val ben = CookCandidate("c-1", "Ben", setOf(tuesday, wednesday), benDeadline)

    // ---- Gericht-Screen ----

    private fun editState(
        mode: EditorMode,
        existing: Dish? = childDish,
        cookId: String? = "c-1",
        allowed: Set<LocalDate> = setOf(wednesday),
        date: LocalDate = wednesday,
        now: Instant = Instant.parse("2026-10-14T10:00:00Z"),
    ) = DishEditUiState(
        dishId = existing?.id,
        weekStart = planWeek,
        date = date,
        name = existing?.name.orEmpty(),
        ingredientsText = existing?.ingredients?.joinToString("\n").orEmpty(),
        cookId = cookId,
        parents = listOf(parentAnna, parentTom),
        children = listOf(ben),
        existing = existing,
        mode = mode,
        allowedDates = allowed,
        isLoading = false,
        rejectReason = "abc",
        now = now,
    )

    private fun showEdit(
        state: DishEditUiState,
        onEvent: (DishEditEvent) -> Unit = {},
    ) = show { DishEditScreen(state, onEvent, {}, {}) }

    @Test
    fun `REQ-MEAL-030 review section shows approve and reject`() {
        val events = mutableListOf<DishEditEvent>()
        showEdit(editState(EditorMode.PARENT_REVIEW), onEvent = { events += it })

        compose.onNodeWithText("Gericht prüfen").assertIsDisplayed()
        compose.onNodeWithText("Ben hat eingereicht").assertIsDisplayed()
        compose.onNodeWithText("Freigeben").performClick()
        compose.onNodeWithText("Ablehnen…").performClick()

        assertEquals(listOf(DishEditEvent.Approve, DishEditEvent.RequestReject), events)
        compose.onNodeWithText("Speichern gibt das Gericht frei.").assertExists()
    }

    @Test
    fun `REQ-MEAL-030 review marks late dish`() {
        showEdit(editState(EditorMode.PARENT_REVIEW, existing = childDish.copy(late = true)))

        compose.onNodeWithText("Ben hat eingereicht · nach Frist").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-030 reject dialog shows counter and error`() {
        showEdit(
            editState(EditorMode.PARENT_REVIEW)
                .copy(showRejectDialog = true, error = AppError.Validation("Bitte eine Begründung angeben.")),
        )

        compose.onNodeWithText("Gericht ablehnen?").assertIsDisplayed()
        compose.onNodeWithText("3/200").assertIsDisplayed()
        compose.onNodeWithText("Bitte eine Begründung angeben.").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-021 move banner names child and day`() {
        showEdit(editState(EditorMode.PARENT_MOVE, existing = childDish.copy(dayValid = false), date = tuesday, allowed = setOf(wednesday)))

        compose
            .onNodeWithText("Ben ist am Di 20.10. nicht da. Gericht auf einen anderen Tag verschieben oder löschen.")
            .assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-021 move mode shows Verschieben and disabled fields`() {
        showEdit(editState(EditorMode.PARENT_MOVE, existing = childDish.copy(dayValid = false), date = wednesday))

        compose.onNodeWithText("Gericht verschieben").assertIsDisplayed()
        compose.onNodeWithText("Curry").assertIsNotEnabled()
        compose.onNodeWithText("Verschieben").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Löschen").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Freigeben").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-021 absent day chip is disabled with nicht da`() {
        showEdit(editState(EditorMode.CHILD, existing = null, date = wednesday))

        compose.onNodeWithContentDescription("Di 20.10. (nicht da)").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mi 21.10.").assertIsEnabled()
    }

    @Test
    fun `REQ-MEAL-020 child editor shows deadline and Einreichen`() {
        showEdit(editState(EditorMode.CHILD, existing = null))

        compose.onNodeWithText("Gericht einreichen").assertIsDisplayed()
        compose.onNodeWithText("Du kochst · Einreichen bis Do 15.10., 16:00").assertIsDisplayed()
        compose.onNodeWithText("Zutaten (eine pro Zeile)").assertExists()
        compose.onNodeWithText("Einreichen").performScrollTo().assertExists()
    }

    @Test
    fun `REQ-MEAL-020 late child sees after deadline hint`() {
        showEdit(editState(EditorMode.CHILD, existing = null, now = Instant.parse("2026-10-17T10:00:00Z")))

        compose.onNodeWithText("Deine Frist ist vorbei – das Gericht wird als „nach Frist“ markiert.").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-020 child editor has no delete`() {
        showEdit(editState(EditorMode.CHILD, existing = childDish.copy(status = DishStatus.APPROVED)))

        compose.onNodeWithText("Gericht ändern").assertIsDisplayed()
        compose.onNodeWithText("Erneut einreichen").performScrollTo().assertExists()
        compose.onNodeWithText("Löschen").assertDoesNotExist()
        compose.onNodeWithText("Nach einer Änderung muss es erneut freigegeben werden.").assertExists()
    }

    @Test
    fun `REQ-MEAL-030 child sees rejection reason`() {
        showEdit(editState(EditorMode.CHILD, existing = childDish.copy(status = DishStatus.REJECTED, rejectionReason = "Mehr Gemüse")))

        compose.onNodeWithText("Abgelehnt: Mehr Gemüse").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-021 child with invalid day is asked to choose another day`() {
        showEdit(editState(EditorMode.CHILD, existing = childDish.copy(dayValid = false), date = tuesday))

        compose.onNodeWithText("Du bist an diesem Tag nicht da – bitte wähle einen anderen Tag.").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-021 child without free day sees hint`() {
        showEdit(editState(EditorMode.CHILD, existing = null, allowed = emptySet(), date = tuesday))

        compose.onNodeWithText("Du bist an keinem freien Tag dieser Woche da.").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-031 cook picker marks children`() {
        showEdit(editState(EditorMode.PARENT_EDIT, existing = null, cookId = "p-1", allowed = setOf(tuesday)))

        compose.onNodeWithText("Anna").performClick()

        compose.onNodeWithText("Ben (Kind)").assertIsDisplayed()
    }

    // ---- Essensplan ----

    private val monday = planWeek

    private fun snapshot(vararg dishes: Pair<Dish, String>) =
        WeekPlanSnapshot(
            planWeek,
            (0L..6L).map { offset ->
                val date = planWeek.plusDays(offset)
                val match = dishes.firstOrNull { it.first.date == date }
                PlannedDay(date, match?.first, match?.second)
            },
            Instant.parse("2026-10-17T10:00:00Z"),
        )

    private fun overview(
        vararg cooks: CookOverview,
        ready: Boolean = false,
        openDays: List<LocalDate> = emptyList(),
    ) = StatusOverview(planWeek, Instant.parse("2026-10-25T15:00:00Z"), ready, openDays, cooks.toList())

    private fun benCook(
        state: CookState = CookState.NOT_SUBMITTED,
        dishId: String? = null,
        late: Boolean = false,
        dayValid: Boolean = true,
        reason: String? = null,
    ) = CookOverview(cookStatus("c-1", state, benDeadline, dishId, late, dayValid, reason), "Ben")

    private fun planState(
        isParent: Boolean,
        week: WeekPlanSnapshot? = snapshot(),
        status: StatusOverview? = null,
        weekStart: LocalDate = planWeek,
        isOffline: Boolean = false,
        cancelTarget: String? = null,
        statusError: AppError? = null,
    ) = WeekPlanUiState(
        weekStart = weekStart,
        today = LocalDate.of(2026, 10, 17),
        week = week,
        isOffline = isOffline,
        isParent = isParent,
        planWeek = planWeek,
        memberId = if (isParent) "p-1" else "c-1",
        status = status,
        statusError = statusError,
        cancelTarget = cancelTarget,
    )

    private fun showPlan(
        state: WeekPlanUiState,
        onEvent: (WeekPlanEvent) -> Unit = {},
        onOpenEditor: (DishEditArgs) -> Unit = {},
    ) = show { WeekPlanScreen(state, onEvent, onOpenEditor, {}, {}) }

    private fun dishOnMonday(
        status: DishStatus = DishStatus.APPROVED,
        late: Boolean = false,
        dayValid: Boolean = true,
        cookId: String = "c-1",
    ) = childDish.copy(date = monday, status = status, late = late, dayValid = dayValid, cookId = cookId)

    @Test
    fun `REQ-MEAL-020 submitted dish shows wartet auf Freigabe badge`() {
        showPlan(planState(true, snapshot(dishOnMonday(DishStatus.SUBMITTED) to "Ben")))

        compose.onNodeWithText("wartet auf Freigabe").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-030 rejected dish shows abgelehnt badge`() {
        showPlan(planState(true, snapshot(dishOnMonday(DishStatus.REJECTED) to "Ben")))

        compose.onNodeWithText("abgelehnt").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-020 late dish shows nach Frist`() {
        showPlan(planState(true, snapshot(dishOnMonday(late = true) to "Ben")))

        compose.onNodeWithText("nach Frist").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-021 invalid day shows Tag ungültig`() {
        showPlan(planState(true, snapshot(dishOnMonday(dayValid = false) to "Ben")))

        compose.onNodeWithText("⚠ Tag ungültig").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-033 status card shows deadline open days and cooks`() {
        val open = listOf(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24))
        showPlan(planState(true, status = overview(benCook(CookState.SUBMITTED, "d-2"), openDays = open)))

        compose.onNodeWithText("Wochenstatus").assertIsDisplayed()
        compose.onNodeWithText("Bestellfrist So 25.10., 16:00").assertIsDisplayed()
        compose.onNodeWithText("Offen: Fr 23.10., Sa 24.10.").assertIsDisplayed()
        compose.onNodeWithText("Ben – wartet auf Freigabe").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-033 ready week shows Bereit zur Bestellung`() {
        showPlan(planState(true, status = overview(benCook(CookState.APPROVED, "d-2"), ready = true)))

        compose.onNodeWithText("✓ Bereit zur Bestellung").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-033 not submitted cook shows deadline`() {
        showPlan(planState(true, status = overview(benCook())))

        compose.onNodeWithText("Ben – noch nicht eingereicht · bis Do 15.10., 16:00").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-033 status error is shown in the card`() {
        showPlan(planState(true, statusError = AppError.Server))

        compose.onNodeWithText("Wochenstatus").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-031 parent can enter dish for child`() {
        val opened = mutableListOf<DishEditArgs>()
        val events = mutableListOf<WeekPlanEvent>()
        showPlan(planState(true, status = overview(benCook())), onEvent = { events += it }, onOpenEditor = { opened += it })

        compose.onNodeWithText("Eintragen").performClick()
        compose.onNodeWithText("Absagen…").performClick()

        assertEquals(listOf(DishEditArgs(planWeek, null, "c-1")), opened)
        assertEquals(listOf<WeekPlanEvent>(WeekPlanEvent.RequestCancel("c-1")), events)
    }

    @Test
    fun `REQ-MEAL-033 parent opens the dish of a cook from the status`() {
        val opened = mutableListOf<DishEditArgs>()
        showPlan(
            planState(true, snapshot(dishOnMonday(DishStatus.SUBMITTED) to "Ben"), overview(benCook(CookState.SUBMITTED, "d-2"))),
            onOpenEditor = { opened += it },
        )

        compose.onNodeWithText("Ben – wartet auf Freigabe").performClick()

        assertEquals(listOf(DishEditArgs(monday, "d-2")), opened)
    }

    @Test
    fun `REQ-MEAL-023 cancelled cook shows reason and withdraw for parents`() {
        val events = mutableListOf<WeekPlanEvent>()
        showPlan(planState(true, status = overview(benCook(CookState.CANCELLED, reason = "krank"))), onEvent = { events += it })

        compose.onNodeWithText("Ben – abgesagt: krank").assertIsDisplayed()
        compose.onNodeWithText("Zurücknehmen").performClick()

        assertEquals(listOf<WeekPlanEvent>(WeekPlanEvent.Withdraw("c-1")), events)
    }

    @Test
    fun `REQ-MEAL-033 child sees status without actions`() {
        showPlan(planState(false, status = overview(benCook(), CookOverview(cookStatus("c-2", CookState.CANCELLED), "Lea"))))

        compose.onNodeWithText("Wochenstatus").assertIsDisplayed()
        compose.onNodeWithText("Eintragen").assertDoesNotExist()
        compose.onNodeWithText("Absagen…").assertDoesNotExist()
        compose.onNodeWithText("Zurücknehmen").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-020 my cooking card shows deadline and submit button`() {
        val opened = mutableListOf<DishEditArgs>()
        showPlan(planState(false, status = overview(benCook())), onOpenEditor = { opened += it })

        compose.onNodeWithText("Mein Kochtag").assertIsDisplayed()
        compose.onNodeWithText("Einreichen bis Do 15.10., 16:00").assertIsDisplayed()
        compose.onNodeWithText("Noch nicht eingereicht").assertIsDisplayed()
        compose.onNodeWithText("Gericht einreichen").performClick()

        assertEquals(listOf(DishEditArgs(planWeek, null, "c-1")), opened)
    }

    @Test
    fun `REQ-MEAL-030 my cooking card shows rejection reason`() {
        val rejected = childDish.copy(date = monday, status = DishStatus.REJECTED, rejectionReason = "Mehr Gemüse")
        val opened = mutableListOf<DishEditArgs>()
        showPlan(
            planState(false, snapshot(rejected to "Ben"), overview(benCook(CookState.REJECTED, "d-2"))),
            onOpenEditor = { opened += it },
        )

        compose.onNodeWithText("Abgelehnt: Mehr Gemüse").assertIsDisplayed()
        compose.onNodeWithText("Gericht überarbeiten").performClick()

        assertEquals(listOf(DishEditArgs(monday, "d-2", "c-1")), opened)
    }

    @Test
    fun `REQ-MEAL-021 my cooking card warns about invalid day`() {
        showPlan(planState(false, status = overview(benCook(CookState.APPROVED, "d-2", dayValid = false))))

        compose.onNodeWithText("Du bist an deinem Kochtag nicht da – bitte verschiebe dein Gericht.").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-023 my cooking card offers cancel and withdraw`() {
        val events = mutableListOf<WeekPlanEvent>()
        showPlan(planState(false, status = overview(benCook())), onEvent = { events += it })
        compose.onNodeWithText("Kochen absagen").performClick()

        assertEquals(listOf<WeekPlanEvent>(WeekPlanEvent.RequestCancel("c-1")), events)
    }

    @Test
    fun `REQ-MEAL-023 my cooking card shows cancellation and withdraw`() {
        val events = mutableListOf<WeekPlanEvent>()
        showPlan(planState(false, status = overview(benCook(CookState.CANCELLED, reason = "krank"))), onEvent = { events += it })

        compose.onNodeWithText("Abgesagt: krank").assertIsDisplayed()
        compose.onNodeWithText("Absage zurücknehmen").performClick()

        assertEquals(listOf<WeekPlanEvent>(WeekPlanEvent.Withdraw("c-1")), events)
    }

    @Test
    fun `REQ-MEAL-023 cancel dialog for the child itself`() {
        showPlan(planState(false, status = overview(benCook()), cancelTarget = "c-1"))

        compose.onNodeWithText("Kochen absagen?").assertIsDisplayed()
        compose.onNodeWithText("Du kochst diese Woche nicht. Dein Gericht wird entfernt.").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-023 cancel dialog for a sibling names the child`() {
        showPlan(planState(true, status = overview(benCook()), cancelTarget = "c-1"))

        compose.onNodeWithText("Ben kocht diese Woche nicht. Ein eingereichtes Gericht wird entfernt.").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-022 child taps own dish row to edit`() {
        val opened = mutableListOf<DishEditArgs>()
        showPlan(planState(false, snapshot(dishOnMonday() to "Ben")), onOpenEditor = { opened += it })

        compose.onNodeWithText("Curry").performClick()

        assertEquals(listOf(DishEditArgs(monday, "d-2")), opened)
    }

    @Test
    fun `REQ-MEAL-022 child cannot open other dishes`() {
        val opened = mutableListOf<DishEditArgs>()
        showPlan(planState(false, snapshot(dishOnMonday(cookId = "p-1") to "Anna")), onOpenEditor = { opened += it })

        compose.onNodeWithText("Curry").performClick()

        assertTrue(opened.isEmpty())
    }

    @Test
    fun `REQ-MEAL-033 plan week link is shown on this week`() {
        val events = mutableListOf<WeekPlanEvent>()
        showPlan(planState(true, weekStart = planWeek.minusWeeks(1)), onEvent = { events += it })

        compose.onNodeWithText("Zur Planwoche 19.10.–25.10. ›").performClick()

        assertEquals(listOf<WeekPlanEvent>(WeekPlanEvent.GoToPlanWeek), events)
    }

    @Test
    fun `REQ-MEAL-033 offline plan week shows status hint`() {
        showPlan(planState(true, isOffline = true))

        compose.onNodeWithText("Wochenstatus nur im Heim-WLAN.").assertIsDisplayed()
        compose.onNodeWithText("Wochenstatus").assertDoesNotExist()
    }
}
