package de.familyplanner.android.feature.meals.ui

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.meals.domain.cooking.ReviewDishUseCase
import de.familyplanner.android.feature.meals.domain.plan.DeleteDishUseCase
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishUseCases
import de.familyplanner.android.feature.meals.domain.plan.LoadDishEditorUseCase
import de.familyplanner.android.feature.meals.domain.plan.SaveDishUseCase
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.domain.recipe.LoadRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.RecipeRef
import de.familyplanner.android.feature.meals.fakes.FakeAttendanceRepository
import de.familyplanner.android.feature.meals.fakes.FakeCookingRepository
import de.familyplanner.android.feature.meals.fakes.FakeMealPlanRepository
import de.familyplanner.android.feature.meals.fakes.FakeRecipeRepository
import de.familyplanner.android.feature.meals.fakes.carbonara
import de.familyplanner.android.feature.meals.fakes.childDish
import de.familyplanner.android.feature.meals.fakes.childSession
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.recipeUseCases
import de.familyplanner.android.feature.meals.fakes.thisWeek
import de.familyplanner.android.feature.meals.fakes.weekPlanOf
import de.familyplanner.android.feature.meals.ui.dish.DishEditArgs
import de.familyplanner.android.feature.meals.ui.dish.DishEditEvent
import de.familyplanner.android.feature.meals.ui.dish.DishEditViewModel
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditArgs
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditEvent
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditViewModel
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEvent
import de.familyplanner.android.feature.meals.ui.recipe.RecipeListArgs
import de.familyplanner.android.feature.meals.ui.recipe.RecipeViewArgs
import de.familyplanner.android.feature.meals.ui.recipe.RecipeViewModel
import de.familyplanner.android.feature.meals.ui.recipes.RecipeListEvent
import de.familyplanner.android.feature.meals.ui.recipes.RecipeListViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val recipes = FakeRecipeRepository()
    private val plan = FakeMealPlanRepository()
    private val attendance = FakeAttendanceRepository()
    private var sessions = InMemorySessionStore(parentSession)

    private fun listViewModel(pick: Boolean = false) = RecipeListViewModel(RecipeListArgs(pick), sessions, recipeUseCases(recipes))

    private fun viewViewModel(args: RecipeViewArgs = RecipeViewArgs("r-1")) = RecipeViewModel(args, sessions, recipeUseCases(recipes), plan)

    private fun editViewModel(recipeId: String? = null) = RecipeEditViewModel(RecipeEditArgs(recipeId), sessions, recipeUseCases(recipes))

    private fun dishViewModel(
        date: LocalDate = planWeek.plusDays(1),
        dishId: String? = null,
    ) = DishEditViewModel(
        DishEditArgs(date, dishId),
        sessions,
        DishUseCases(
            LoadDishEditorUseCase(plan, attendance),
            SaveDishUseCase(plan),
            DeleteDishUseCase(plan),
            ReviewDishUseCase(FakeCookingRepository()),
            LoadRecipeUseCase(recipes),
        ),
    )

    private fun messageOf(error: AppError?) = (error as? AppError.Validation)?.message

    // ---- Liste ----

    @Test
    fun `REQ-MEAL-060 list loads recipes`() {
        val viewModel = listViewModel()

        assertEquals(2, viewModel.state.value.recipes.size)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `REQ-MEAL-060 search filters loaded list without request`() {
        val viewModel = listViewModel()

        viewModel.onEvent(RecipeListEvent.QueryChanged("LAUCH"))

        assertEquals(
            listOf("r-1"),
            viewModel.state.value.visible
                .map { it.id },
        )
        assertEquals(1, recipes.listCalls)
    }

    @Test
    fun `REQ-MEAL-060 search query is limited in length`() {
        val viewModel = listViewModel()

        viewModel.onEvent(RecipeListEvent.QueryChanged("x".repeat(100)))

        assertEquals(60, viewModel.state.value.query.length)
    }

    @Test
    fun `REQ-MEAL-060 refresh loads again`() {
        val viewModel = listViewModel()

        viewModel.onEvent(RecipeListEvent.Refresh)

        assertEquals(2, recipes.listCalls)
    }

    @Test
    fun `REQ-MEAL-060 child cannot create`() {
        sessions = InMemorySessionStore(childSession)

        assertFalse(listViewModel().state.value.canCreate)
    }

    @Test
    fun `REQ-MEAL-060 parent can create`() {
        assertTrue(listViewModel().state.value.canCreate)
    }

    @Test
    fun `REQ-MEAL-062 pick mode hides create`() {
        assertFalse(listViewModel(pick = true).state.value.canCreate)
    }

    @Test
    fun `REQ-MEAL-060 load error is shown`() {
        recipes.listResult = ApiResult.Failure(AppError.Network)

        assertEquals(AppError.Network, listViewModel().state.value.error)
    }

    // ---- Ansicht ----

    @Test
    fun `REQ-MEAL-060 view shows recipe for all members`() {
        sessions = InMemorySessionStore(childSession)

        val state = viewViewModel().state.value

        assertEquals("Spaghetti mit Lauch-Carbonara", state.recipe?.name)
        assertEquals(listOf("500 g Vollkornspaghetti", "Salz", "2 Stange(n) Lauch"), state.ingredientLines)
        assertEquals(4, state.servings)
    }

    @Test
    fun `REQ-MEAL-063 view from dish shows dish ingredients and servings`() {
        val dish = Dish("d-9", thisWeek.plusDays(1), "p-1", "Carbonara", listOf("625 g Vollkornspaghetti"), recipeId = "r-1", servings = 5)
        plan.weekResult = { ApiResult.Success(weekPlanOf(it, dish)) }

        val state = viewViewModel(RecipeViewArgs("r-1", dish.date, "d-9")).state.value

        assertEquals(listOf("625 g Vollkornspaghetti"), state.ingredientLines)
        assertEquals(5, state.servings)
        assertEquals(listOf(thisWeek), plan.requestedWeeks)
    }

    @Test
    fun `REQ-MEAL-063 missing dish falls back to recipe quantities`() {
        plan.weekResult = { ApiResult.Success(weekPlanOf(it)) }

        val state = viewViewModel(RecipeViewArgs("r-1", thisWeek, "gone")).state.value

        assertEquals(4, state.servings)
        assertEquals("500 g Vollkornspaghetti", state.ingredientLines.first())
        assertNull(state.error)
    }

    @Test
    fun `REQ-MEAL-063 unreadable week falls back to recipe quantities`() {
        plan.weekResult = { ApiResult.Failure(AppError.Network) }

        val state = viewViewModel(RecipeViewArgs("r-1", thisWeek, "d-1")).state.value

        assertEquals(4, state.servings)
        assertNull(state.error)
    }

    @Test
    fun `REQ-MEAL-060 view of deleted recipe shows error`() {
        recipes.recipeResult = { ApiResult.Failure(AppError.NotFound) }

        assertEquals(AppError.NotFound, viewViewModel().state.value.error)
    }

    @Test
    fun `REQ-MEAL-060 parent deletes recipe`() {
        val viewModel = viewViewModel()
        viewModel.onEvent(RecipeEvent.RequestDelete)
        assertTrue(viewModel.state.value.showDeleteConfirm)

        viewModel.onEvent(RecipeEvent.ConfirmDelete)

        assertEquals(listOf("r-1"), recipes.deleted)
        assertTrue(viewModel.state.value.isDeleted)
    }

    @Test
    fun `REQ-MEAL-060 cancelling the confirmation keeps the recipe`() {
        val viewModel = viewViewModel()
        viewModel.onEvent(RecipeEvent.RequestDelete)

        viewModel.onEvent(RecipeEvent.CancelDelete)

        assertFalse(viewModel.state.value.showDeleteConfirm)
        assertTrue(recipes.deleted.isEmpty())
    }

    @Test
    fun `REQ-MEAL-060 deleting an already deleted recipe counts as done`() {
        recipes.deleteResult = ApiResult.Failure(AppError.NotFound)
        val viewModel = viewViewModel()

        viewModel.onEvent(RecipeEvent.ConfirmDelete)

        assertTrue(viewModel.state.value.isDeleted)
    }

    @Test
    fun `REQ-MEAL-060 failed delete shows error and stays`() {
        recipes.deleteResult = ApiResult.Failure(AppError.Network)
        val viewModel = viewViewModel()

        viewModel.onEvent(RecipeEvent.ConfirmDelete)

        assertFalse(viewModel.state.value.isDeleted)
        assertEquals(AppError.Network, viewModel.state.value.actionError)
    }

    @Test
    fun `REQ-MEAL-060 child cannot manage`() {
        sessions = InMemorySessionStore(childSession)
        val viewModel = viewViewModel()

        viewModel.onEvent(RecipeEvent.ConfirmDelete)

        assertFalse(viewModel.state.value.canManage)
        assertTrue(recipes.deleted.isEmpty())
    }

    @Test
    fun `REQ-MEAL-063 view from dish cannot manage`() {
        val dish = Dish("d-9", thisWeek.plusDays(1), "p-1", "Carbonara", listOf("625 g Vollkornspaghetti"), recipeId = "r-1", servings = 5)
        plan.weekResult = { ApiResult.Success(weekPlanOf(it, dish)) }

        assertFalse(viewViewModel(RecipeViewArgs("r-1", dish.date, "d-9")).state.value.canManage)
    }

    @Test
    fun `REQ-MEAL-063 view from dish cannot manage even when the dish is gone`() {
        plan.weekResult = { ApiResult.Success(weekPlanOf(it)) }

        assertFalse(viewViewModel(RecipeViewArgs("r-1", thisWeek, "gone")).state.value.canManage)
    }

    // ---- Rezept-Editor ----

    @Test
    fun `REQ-MEAL-060 editor loads existing recipe as text`() {
        val state = editViewModel("r-1").state.value

        assertEquals("Spaghetti mit Lauch-Carbonara", state.name)
        assertEquals("500 g Vollkornspaghetti\nSalz\n2 Stange(n) Lauch", state.ingredientsText)
        assertEquals("Nudeln kochen.\nLauch schneiden.", state.stepsText)
        assertEquals(carbonara.sourceUrl, state.sourceUrl)
        assertEquals(4, state.servings)
    }

    @Test
    fun `REQ-MEAL-060 new recipe starts with four servings`() {
        val state = editViewModel().state.value

        assertTrue(state.isNew)
        assertEquals(4, state.servings)
        assertFalse(state.canSave)
    }

    @Test
    fun `REQ-MEAL-060 child editor is forbidden without request`() {
        sessions = InMemorySessionStore(childSession)

        val state = editViewModel("r-1").state.value

        assertEquals(AppError.Forbidden, state.loadError)
        assertTrue(recipes.requested.isEmpty())
    }

    @Test
    fun `REQ-MEAL-060 editor load error is shown`() {
        recipes.recipeResult = { ApiResult.Failure(AppError.NotFound) }

        assertEquals(AppError.NotFound, editViewModel("r-1").state.value.loadError)
    }

    @Test
    fun `REQ-MEAL-060 servings stay within 1 and 20`() {
        val viewModel = editViewModel()

        viewModel.onEvent(RecipeEditEvent.ServingsChanged(0))
        assertEquals(1, viewModel.state.value.servings)
        viewModel.onEvent(RecipeEditEvent.ServingsChanged(21))
        assertEquals(20, viewModel.state.value.servings)
    }

    @Test
    fun `REQ-MEAL-060 save creates recipe and finishes`() {
        val viewModel = editViewModel()
        viewModel.onEvent(RecipeEditEvent.NameChanged(" Curry "))
        viewModel.onEvent(RecipeEditEvent.IngredientsChanged("200 g Reis\nSalz"))
        viewModel.onEvent(RecipeEditEvent.StepsChanged("Kochen.\n\nEssen."))

        viewModel.onEvent(RecipeEditEvent.Save)

        val draft = recipes.created.single()
        assertEquals("Curry", draft.name)
        assertEquals(listOf(Ingredient("Reis", 200.0, "g"), Ingredient("Salz")), draft.ingredients)
        assertEquals(listOf("Kochen.", "Essen."), draft.steps)
        assertNull(draft.sourceUrl)
        assertTrue(viewModel.state.value.isDone)
    }

    @Test
    fun `REQ-MEAL-060 save updates existing recipe`() {
        val viewModel = editViewModel("r-1")

        viewModel.onEvent(RecipeEditEvent.Save)

        assertEquals("r-1", recipes.updated.single().first)
        assertTrue(viewModel.state.value.isDone)
    }

    @Test
    fun `REQ-MEAL-060 duplicate name shows server message`() {
        recipes.createResult = ApiResult.Failure(AppError.Conflict("Ein Rezept mit diesem Namen gibt es schon"))
        val viewModel = editViewModel()
        viewModel.onEvent(RecipeEditEvent.NameChanged("Curry"))
        viewModel.onEvent(RecipeEditEvent.IngredientsChanged("Reis"))

        viewModel.onEvent(RecipeEditEvent.Save)

        assertEquals(AppError.Conflict("Ein Rezept mit diesem Namen gibt es schon"), viewModel.state.value.error)
        assertFalse(viewModel.state.value.isDone)
    }

    @Test
    fun `REQ-MEAL-060 invalid url shows validation without request`() {
        val viewModel = editViewModel()
        viewModel.onEvent(RecipeEditEvent.NameChanged("Curry"))
        viewModel.onEvent(RecipeEditEvent.IngredientsChanged("Reis"))
        viewModel.onEvent(RecipeEditEvent.SourceUrlChanged("rewe.de/rezepte"))

        viewModel.onEvent(RecipeEditEvent.Save)

        assertEquals("Bitte einen gültigen Link (http/https) angeben.", messageOf(viewModel.state.value.error))
        assertTrue(recipes.created.isEmpty())
    }

    @Test
    fun `REQ-MEAL-060 editing clears the previous error`() {
        recipes.createResult = ApiResult.Failure(AppError.Network)
        val viewModel = editViewModel()
        viewModel.onEvent(RecipeEditEvent.NameChanged("Curry"))
        viewModel.onEvent(RecipeEditEvent.IngredientsChanged("Reis"))
        viewModel.onEvent(RecipeEditEvent.Save)

        viewModel.onEvent(RecipeEditEvent.NameChanged("Curry 2"))

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `REQ-MEAL-060 double save sends once`() {
        val gate = CompletableDeferred<Unit>()
        recipes.createGate = gate
        val viewModel = editViewModel()
        viewModel.onEvent(RecipeEditEvent.NameChanged("Curry"))
        viewModel.onEvent(RecipeEditEvent.IngredientsChanged("Reis"))

        viewModel.onEvent(RecipeEditEvent.Save)
        viewModel.onEvent(RecipeEditEvent.Save)
        gate.complete(Unit)

        assertEquals(1, recipes.created.size)
    }

    @Test
    fun `REQ-MEAL-060 retry reloads the recipe`() {
        recipes.recipeResult = { ApiResult.Failure(AppError.Network) }
        val viewModel = editViewModel("r-1")
        recipes.recipeResult = { ApiResult.Success(carbonara) }

        viewModel.onEvent(RecipeEditEvent.Retry)

        assertNull(viewModel.state.value.loadError)
        assertEquals(carbonara.name, viewModel.state.value.name)
    }

    // ---- Gericht-Editor mit Rezept ----

    @Test
    fun `REQ-MEAL-062 picking a recipe scales ingredients to present members`() {
        val viewModel = dishViewModel()

        viewModel.onEvent(DishEditEvent.RecipePicked("r-1"))

        val state = viewModel.state.value
        assertEquals(3, state.servings)
        assertEquals("375 g Vollkornspaghetti\nSalz\n1,5 Stange(n) Lauch", state.ingredientsText)
    }

    @Test
    fun `REQ-MEAL-062 pick arriving during load is applied after load`() {
        val gate = CompletableDeferred<Unit>()
        plan.weekGates[planWeek] = gate
        val viewModel = dishViewModel()

        viewModel.onEvent(DishEditEvent.RecipePicked("r-1"))
        assertNull(viewModel.state.value.recipeId)
        gate.complete(Unit)

        assertEquals("r-1", viewModel.state.value.recipeId)
        assertEquals(3, viewModel.state.value.servings)
    }

    @Test
    fun `REQ-MEAL-062 picking a recipe sets name`() {
        val viewModel = dishViewModel()

        viewModel.onEvent(DishEditEvent.RecipePicked("r-1"))

        assertEquals("Spaghetti mit Lauch-Carbonara", viewModel.state.value.name)
        assertEquals("r-1", viewModel.state.value.recipeId)
    }

    @Test
    fun `REQ-MEAL-062 changing servings rescales from recipe`() {
        val viewModel = dishViewModel()
        viewModel.onEvent(DishEditEvent.RecipePicked("r-1"))
        viewModel.onEvent(DishEditEvent.IngredientsChanged("manuell geändert"))

        viewModel.onEvent(DishEditEvent.ServingsChanged(5))

        assertEquals("625 g Vollkornspaghetti\nSalz\n2,5 Stange(n) Lauch", viewModel.state.value.ingredientsText)
    }

    @Test
    fun `REQ-MEAL-062 servings are clamped to 1 and 20`() {
        val viewModel = dishViewModel()
        viewModel.onEvent(DishEditEvent.RecipePicked("r-1"))

        viewModel.onEvent(DishEditEvent.ServingsChanged(0))
        assertEquals(1, viewModel.state.value.servings)
        viewModel.onEvent(DishEditEvent.ServingsChanged(99))
        assertEquals(20, viewModel.state.value.servings)
    }

    @Test
    fun `REQ-MEAL-062 save sends recipe reference and parsed ingredients`() {
        val viewModel = dishViewModel()
        viewModel.onEvent(DishEditEvent.RecipePicked("r-1"))
        viewModel.onEvent(DishEditEvent.ServingsChanged(5))

        viewModel.onEvent(DishEditEvent.Save)

        val draft = plan.created.single()
        assertEquals(RecipeRef("r-1", 5), draft.recipe)
        assertEquals(Ingredient("Vollkornspaghetti", 625.0, "g"), draft.ingredients.first())
        assertEquals(Ingredient("Salz"), draft.ingredients[1])
    }

    @Test
    fun `REQ-MEAL-062 edited ingredients after pick keep the recipe reference`() {
        val viewModel = dishViewModel()
        viewModel.onEvent(DishEditEvent.RecipePicked("r-1"))

        viewModel.onEvent(DishEditEvent.IngredientsChanged("500 g Vollkornspaghetti"))
        viewModel.onEvent(DishEditEvent.Save)

        assertEquals(
            "r-1",
            plan.created
                .single()
                .recipe
                ?.recipeId,
        )
    }

    @Test
    fun `REQ-MEAL-062 clearing the recipe keeps text and drops reference`() {
        val viewModel = dishViewModel()
        viewModel.onEvent(DishEditEvent.RecipePicked("r-1"))
        val text = viewModel.state.value.ingredientsText

        viewModel.onEvent(DishEditEvent.ClearRecipe)

        val state = viewModel.state.value
        assertNull(state.recipeId)
        assertNull(state.servings)
        assertNull(state.recipe)
        assertEquals(text, state.ingredientsText)
        assertEquals("Spaghetti mit Lauch-Carbonara", state.name)
        viewModel.onEvent(DishEditEvent.Save)
        assertNull(plan.created.single().recipe)
    }

    @Test
    fun `REQ-MEAL-062 without recipe dish is saved as before`() {
        val viewModel = dishViewModel()
        viewModel.onEvent(DishEditEvent.NameChanged("Curry"))
        viewModel.onEvent(DishEditEvent.IngredientsChanged("500g Mehl"))

        viewModel.onEvent(DishEditEvent.Save)

        val draft = plan.created.single()
        assertNull(draft.recipe)
        assertEquals(listOf(Ingredient("Mehl", 500.0, "g")), draft.ingredients)
    }

    @Test
    fun `REQ-MEAL-062 existing dish keeps its recipe reference`() {
        val existing =
            Dish("d-9", thisWeek.plusDays(1), "p-1", "Carbonara", listOf("625 g Vollkornspaghetti"), recipeId = "r-1", servings = 5)
        plan.weekResult = { ApiResult.Success(weekPlanOf(it, existing)) }
        val viewModel = dishViewModel(existing.date, "d-9")

        assertEquals("r-1", viewModel.state.value.recipeId)
        assertEquals(
            carbonara.name,
            viewModel.state.value.recipe
                ?.name,
        )
        viewModel.onEvent(DishEditEvent.Save)
        assertEquals(
            RecipeRef("r-1", 5),
            plan.updated
                .single()
                .second.recipe,
        )
    }

    @Test
    fun `REQ-MEAL-062 existing dish keeps reference when recipe cannot be loaded`() {
        val existing = Dish("d-9", thisWeek.plusDays(1), "p-1", "Carbonara", listOf("Nudeln"), recipeId = "r-1", servings = 5)
        plan.weekResult = { ApiResult.Success(weekPlanOf(it, existing)) }
        recipes.recipeResult = { ApiResult.Failure(AppError.NotFound) }
        val viewModel = dishViewModel(existing.date, "d-9")

        assertNull(viewModel.state.value.recipe)
        viewModel.onEvent(DishEditEvent.ServingsChanged(2))
        assertEquals(5, viewModel.state.value.servings)
        viewModel.onEvent(DishEditEvent.Save)
        assertEquals(
            RecipeRef("r-1", 5),
            plan.updated
                .single()
                .second.recipe,
        )
    }

    @Test
    fun `REQ-MEAL-062 failed recipe load shows error`() {
        recipes.recipeResult = { ApiResult.Failure(AppError.NotFound) }
        val viewModel = dishViewModel()

        viewModel.onEvent(DishEditEvent.RecipePicked("r-1"))

        assertEquals(AppError.NotFound, viewModel.state.value.error)
        assertNull(viewModel.state.value.recipeId)
        assertFalse(viewModel.state.value.isRecipeLoading)
    }

    @Test
    fun `REQ-MEAL-062 child can pick a recipe`() {
        sessions = InMemorySessionStore(childSession)
        val viewModel = dishViewModel()

        viewModel.onEvent(DishEditEvent.RecipePicked("r-1"))

        assertEquals("r-1", viewModel.state.value.recipeId)
        assertEquals(3, viewModel.state.value.servings)
    }

    @Test
    fun `REQ-MEAL-062 move mode ignores recipe events`() {
        plan.weekResult = { ApiResult.Success(weekPlanOf(it, childDish.copy(dayValid = false))) }
        val viewModel = dishViewModel(childDish.date, "d-2")
        assertTrue(viewModel.state.value.fieldsLocked)

        viewModel.onEvent(DishEditEvent.RecipePicked("r-1"))

        assertNull(viewModel.state.value.recipeId)
        assertTrue(recipes.requested.isEmpty())
    }
}
