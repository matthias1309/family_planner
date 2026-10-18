package de.familyplanner.android.feature.meals.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import de.familyplanner.android.feature.meals.fakes.carbonara
import de.familyplanner.android.feature.meals.fakes.carbonaraSummary
import de.familyplanner.android.feature.meals.fakes.parentAnna
import de.familyplanner.android.feature.meals.fakes.parentTom
import de.familyplanner.android.feature.meals.fakes.quesadilla
import de.familyplanner.android.feature.meals.fakes.thisWeek
import de.familyplanner.android.feature.meals.ui.dish.DishEditEvent
import de.familyplanner.android.feature.meals.ui.dish.DishEditScreen
import de.familyplanner.android.feature.meals.ui.dish.DishEditUiState
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanScreen
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanUiState
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditEvent
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditScreen
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditUiState
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEvent
import de.familyplanner.android.feature.meals.ui.recipe.RecipeScreen
import de.familyplanner.android.feature.meals.ui.recipe.RecipeUiState
import de.familyplanner.android.feature.meals.ui.recipe.RecipeViewArgs
import de.familyplanner.android.feature.meals.ui.recipes.RecipeListEvent
import de.familyplanner.android.feature.meals.ui.recipes.RecipeListScreen
import de.familyplanner.android.feature.meals.ui.recipes.RecipeListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RecipeScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { FamilyPlannerTheme(content = content) }

    // ---- Liste ----

    private fun listState(
        query: String = "",
        isParent: Boolean = false,
        pick: Boolean = false,
    ) = RecipeListUiState(recipes = listOf(quesadilla, carbonaraSummary), query = query, isParent = isParent, pick = pick)

    private fun showList(
        state: RecipeListUiState,
        onEvent: (RecipeListEvent) -> Unit = {},
        onOpen: (String) -> Unit = {},
        onCreate: () -> Unit = {},
    ) = show { RecipeListScreen(state, onEvent, onOpen, onCreate, {}) }

    @Test
    fun `REQ-MEAL-060 list shows name and servings`() {
        showList(listState())

        compose.onNodeWithText("Bunte Gemüse Quesadilla").assertIsDisplayed()
        compose.onNodeWithText("Spaghetti mit Lauch-Carbonara").assertIsDisplayed()
        compose.onAllNodesWithText("4 Portionen").assertCountEquals(2)
    }

    @Test
    fun `REQ-MEAL-060 search shows only matching recipes`() {
        showList(listState(query = "lauch"))

        compose.onNodeWithText("Spaghetti mit Lauch-Carbonara").assertIsDisplayed()
        compose.onNodeWithText("Bunte Gemüse Quesadilla").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-060 search input is reported`() {
        val events = mutableListOf<RecipeListEvent>()
        showList(listState(), onEvent = { events += it })

        compose.onNodeWithText("Suchen").performTextInput("lau")

        assertEquals(listOf<RecipeListEvent>(RecipeListEvent.QueryChanged("lau")), events)
    }

    @Test
    fun `REQ-MEAL-060 empty search result names the query`() {
        showList(listState(query = "xyz"))

        compose.onNodeWithText("Kein Rezept passt zu „xyz“.").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-060 empty collection shows hint`() {
        showList(RecipeListUiState())

        compose.onNodeWithText("Noch keine Rezepte.").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-060 new recipe button only for parents`() {
        var created = false
        showList(listState(isParent = true), onCreate = { created = true })

        compose.onNodeWithText("Neues Rezept").performScrollTo().performClick()

        assertTrue(created)
    }

    @Test
    fun `REQ-MEAL-060 child sees no new recipe button`() {
        showList(listState(isParent = false))

        compose.onNodeWithText("Neues Rezept").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-062 pick mode shows Rezept wählen and returns tapped id`() {
        val opened = mutableListOf<String>()
        showList(listState(isParent = true, pick = true), onOpen = { opened += it })

        compose.onNodeWithText("Rezept wählen").assertIsDisplayed()
        compose.onNodeWithText("Neues Rezept").assertDoesNotExist()
        compose.onNodeWithText("Spaghetti mit Lauch-Carbonara").performClick()

        assertEquals(listOf("r-1"), opened)
    }

    @Test
    fun `REQ-MEAL-060 list load error is shown`() {
        showList(RecipeListUiState(error = AppError.Network))

        compose.onNodeWithText("Server nicht erreichbar", substring = true).assertExists()
    }

    // ---- Ansicht ----

    private fun showView(
        state: RecipeUiState,
        onEvent: (RecipeEvent) -> Unit = {},
        onEdit: (String) -> Unit = {},
    ) = show { RecipeScreen(state, onEvent, onEdit, {}) }

    @Test
    fun `REQ-MEAL-060 view shows servings ingredients and numbered steps`() {
        showView(RecipeUiState(recipe = carbonara, isLoading = false))

        compose.onNodeWithText("Spaghetti mit Lauch-Carbonara").assertIsDisplayed()
        compose.onNodeWithText("Für 4 Portionen").assertIsDisplayed()
        compose.onNodeWithText("• 500 g Vollkornspaghetti").assertExists()
        compose.onNodeWithText("• 2 Stange(n) Lauch").assertExists()
        compose.onNodeWithText("1. Nudeln kochen.").assertExists()
        compose.onNodeWithText("2. Lauch schneiden.").assertExists()
    }

    @Test
    fun `REQ-MEAL-060 view without steps shows hint`() {
        showView(RecipeUiState(recipe = carbonara.copy(steps = emptyList()), isLoading = false))

        compose.onNodeWithText("Keine Zubereitungsschritte hinterlegt.").assertExists()
    }

    @Test
    fun `REQ-MEAL-060 parent sees edit and delete`() {
        val events = mutableListOf<RecipeEvent>()
        val edited = mutableListOf<String>()
        showView(RecipeUiState(recipe = carbonara, isLoading = false, isParent = true), { events += it }, { edited += it })

        compose.onNodeWithText("Bearbeiten").performScrollTo().performClick()
        compose.onNodeWithText("Löschen").performScrollTo().performClick()

        assertEquals(listOf("r-1"), edited)
        assertEquals(listOf<RecipeEvent>(RecipeEvent.RequestDelete), events)
    }

    @Test
    fun `REQ-MEAL-060 child sees no edit`() {
        showView(RecipeUiState(recipe = carbonara, isLoading = false, isParent = false))

        compose.onNodeWithText("Bearbeiten").assertDoesNotExist()
        compose.onNodeWithText("Löschen").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-060 delete asks for confirmation`() {
        val events = mutableListOf<RecipeEvent>()
        showView(RecipeUiState(recipe = carbonara, isLoading = false, isParent = true, showDeleteConfirm = true), { events += it })

        compose.onNodeWithText("Rezept löschen?").assertIsDisplayed()
        compose.onNodeWithText("Gerichte, die schon daraus übernommen wurden, bleiben unverändert.").assertIsDisplayed()
        compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(listOf<RecipeEvent>(RecipeEvent.ConfirmDelete), events)
    }

    @Test
    fun `REQ-MEAL-063 view from dish shows Für 5 Portionen`() {
        showView(
            RecipeUiState(
                recipe = carbonara,
                dishIngredients = listOf("625 g Vollkornspaghetti", "Salz"),
                dishServings = 5,
                fromDish = true,
                isLoading = false,
                isParent = true,
            ),
        )

        compose.onNodeWithText("Für 5 Portionen").assertIsDisplayed()
        compose.onNodeWithText("• 625 g Vollkornspaghetti").assertExists()
        compose.onNodeWithText("Bearbeiten").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-060 view error offers reload`() {
        val events = mutableListOf<RecipeEvent>()
        showView(RecipeUiState(isLoading = false, error = AppError.Network), { events += it })

        compose.onNodeWithText("Neu laden").performClick()

        assertEquals(listOf<RecipeEvent>(RecipeEvent.Refresh), events)
    }

    // ---- Editor ----

    private fun showEdit(
        state: RecipeEditUiState,
        onEvent: (RecipeEditEvent) -> Unit = {},
    ) = show { RecipeEditScreen(state, onEvent, {}) }

    @Test
    fun `REQ-MEAL-060 editor shows fields and counter`() {
        showEdit(RecipeEditUiState(recipeId = null, name = "Curry"))

        compose.onNodeWithText("Neues Rezept").assertIsDisplayed()
        compose.onNodeWithText("5/60").assertExists()
        compose.onNodeWithText("Zutaten (eine pro Zeile, z. B. 500 g Spaghetti)").assertExists()
        compose.onNodeWithText("Zubereitung (ein Schritt pro Zeile, optional)").assertExists()
        compose.onNodeWithText("Link zum Rezept (optional, z. B. von rewe.de)").assertExists()
    }

    @Test
    fun `REQ-MEAL-060 editor save needs name and ingredients`() {
        showEdit(RecipeEditUiState(recipeId = "r-1"))

        compose.onNodeWithText("Rezept bearbeiten").assertIsDisplayed()
        compose.onNodeWithText("Speichern").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `REQ-MEAL-060 editor save reports event`() {
        val events = mutableListOf<RecipeEditEvent>()
        showEdit(RecipeEditUiState(recipeId = null, name = "Curry", ingredientsText = "Reis"), { events += it })

        compose
            .onNodeWithText("Speichern")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(listOf<RecipeEditEvent>(RecipeEditEvent.Save), events)
    }

    @Test
    fun `REQ-MEAL-060 stepper disables minus at one`() {
        showEdit(RecipeEditUiState(recipeId = null, servings = 1))

        compose.onNodeWithContentDescription("Weniger Portionen").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mehr Portionen").assertIsEnabled()
    }

    @Test
    fun `REQ-MEAL-060 stepper disables plus at twenty`() {
        showEdit(RecipeEditUiState(recipeId = null, servings = 20))

        compose.onNodeWithContentDescription("Mehr Portionen").assertIsNotEnabled()
    }

    @Test
    fun `REQ-MEAL-060 child editor shows forbidden message`() {
        showEdit(RecipeEditUiState(recipeId = "r-1", loadError = AppError.Forbidden))

        compose.onNodeWithText("Nur für Eltern.", substring = true).assertExists()
    }

    @Test
    fun `REQ-MEAL-060 duplicate name message is shown in editor`() {
        showEdit(RecipeEditUiState(recipeId = null, name = "Curry", error = AppError.Conflict("Ein Rezept mit diesem Namen gibt es schon")))

        compose.onNodeWithText("Ein Rezept mit diesem Namen gibt es schon").assertExists()
    }

    // ---- Gericht-Editor ----

    private fun dishState(
        recipeId: String? = null,
        servings: Int? = null,
        recipe: de.familyplanner.android.feature.meals.domain.recipe.Recipe? = null,
        mode: de.familyplanner.android.feature.meals.domain.plan.EditorMode =
            de.familyplanner.android.feature.meals.domain.plan.EditorMode.PARENT_EDIT,
    ) = DishEditUiState(
        dishId = null,
        weekStart = thisWeek,
        date = thisWeek.plusDays(1),
        cookId = "p-1",
        parents = listOf(parentAnna, parentTom),
        allowedDates = (0L..6L).map { thisWeek.plusDays(it) }.toSet(),
        isLoading = false,
        recipeId = recipeId,
        servings = servings,
        recipe = recipe,
        mode = mode,
    )

    private fun showDish(
        state: DishEditUiState,
        onEvent: (DishEditEvent) -> Unit = {},
        onPickRecipe: () -> Unit = {},
    ) = show { DishEditScreen(state, onEvent, onPickRecipe, {}) }

    @Test
    fun `REQ-MEAL-062 editor offers Aus Rezept wählen`() {
        var picked = false
        showDish(dishState(), onPickRecipe = { picked = true })

        compose.onNodeWithText("Aus Rezept wählen").performScrollTo().performClick()

        assertTrue(picked)
    }

    @Test
    fun `REQ-MEAL-062 editor shows recipe name and servings stepper`() {
        val events = mutableListOf<DishEditEvent>()
        showDish(dishState("r-1", 5, carbonara), { events += it })

        compose.onNodeWithText("Aus Rezept „Spaghetti mit Lauch-Carbonara“").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Portionen ändern berechnet die Zutaten neu.").assertExists()
        compose.onNodeWithContentDescription("Mehr Portionen").performScrollTo().performClick()
        compose.onNodeWithText("Rezeptbezug entfernen").performScrollTo().performClick()

        assertEquals(listOf(DishEditEvent.ServingsChanged(6), DishEditEvent.ClearRecipe), events)
    }

    @Test
    fun `REQ-MEAL-062 editor without loaded recipe shows no stepper`() {
        showDish(dishState("r-1", 5, recipe = null))

        compose.onNodeWithText("Aus einem Rezept").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Mehr Portionen").assertDoesNotExist()
        compose.onNodeWithText("Rezeptbezug entfernen").assertExists()
    }

    @Test
    fun `REQ-MEAL-062 move mode shows no recipe section`() {
        showDish(dishState(mode = de.familyplanner.android.feature.meals.domain.plan.EditorMode.PARENT_MOVE))

        compose.onNodeWithText("Aus Rezept wählen").assertDoesNotExist()
    }

    // ---- Plan-Link ----

    private val recipeDish =
        Dish("d-5", thisWeek.plusDays(1), "p-1", "Carbonara", listOf("625 g Spaghetti"), recipeId = "r-1", servings = 5)
    private val plainDish = Dish("d-6", thisWeek.plusDays(2), "p-1", "Pizza", listOf("Teig"))

    private fun planState(
        isOffline: Boolean = false,
        dishes: List<Dish> = listOf(recipeDish, plainDish),
    ) = WeekPlanUiState(
        thisWeek,
        LocalDate.of(2026, 10, 17),
        WeekPlanSnapshot(
            thisWeek,
            (0L..6L).map { offset ->
                val date = thisWeek.plusDays(offset)
                val dish = dishes.firstOrNull { it.date == date }
                PlannedDay(date, dish, dish?.let { "Anna" })
            },
            Instant.parse("2026-10-18T10:00:00Z"),
        ),
        isOffline = isOffline,
        planWeek = thisWeek.plusWeeks(2),
    )

    @Test
    fun `REQ-MEAL-063 plan row with recipe shows Rezept link`() {
        val opened = mutableListOf<RecipeViewArgs>()
        show { WeekPlanScreen(planState(), {}, {}, { opened += it }, {}) }

        compose.onNodeWithText("Rezept ›").performClick()

        assertEquals(listOf(RecipeViewArgs("r-1", recipeDish.date, "d-5")), opened)
    }

    @Test
    fun `REQ-MEAL-063 offline plan shows no recipe link`() {
        show { WeekPlanScreen(planState(isOffline = true), {}, {}, {}, {}) }

        compose.onNodeWithText("Carbonara").assertIsDisplayed()
        compose.onNodeWithText("Rezept ›").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-063 dish without recipe shows no link`() {
        show { WeekPlanScreen(planState(dishes = listOf(plainDish)), {}, {}, {}, {}) }

        compose.onNodeWithText("Pizza").assertIsDisplayed()
        compose.onNodeWithText("Rezept ›").assertDoesNotExist()
    }
}
