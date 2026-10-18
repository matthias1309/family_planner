package de.familyplanner.android.feature.meals.ui

import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.meals.domain.recipe.ImportRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.domain.recipe.RecipeImportError
import de.familyplanner.android.feature.meals.domain.recipe.SourceResult
import de.familyplanner.android.feature.meals.fakes.CARBONARA_URL
import de.familyplanner.android.feature.meals.fakes.FakeRecipeRepository
import de.familyplanner.android.feature.meals.fakes.FakeRecipeSource
import de.familyplanner.android.feature.meals.fakes.childSession
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.fakes.recipeUseCases
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditArgs
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditEvent
import de.familyplanner.android.feature.meals.ui.recipe.RecipeEditViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeImportViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val recipes = FakeRecipeRepository()
    private val source = FakeRecipeSource()
    private var sessions = InMemorySessionStore(parentSession)
    private val shared = "Lauch-Carbonara $CARBONARA_URL"

    private fun viewModel(args: RecipeEditArgs = RecipeEditArgs(importText = shared)) =
        RecipeEditViewModel(args, sessions, recipeUseCases(recipes), ImportRecipeUseCase(source))

    @Test
    fun `REQ-MEAL-061 shared link fills the editor`() {
        val state = viewModel().state.value

        assertEquals("Spaghetti mit Lauch-Carbonara mit & ohne Speck", state.name)
        assertEquals(4, state.servings)
        assertEquals(11, state.ingredientsText.lines().size)
        assertEquals("Nudeln kochen.\nLauch in Ringe schneiden.\nAlles mischen.", state.stepsText)
        assertEquals(CARBONARA_URL, state.sourceUrl)
        assertFalse(state.isImporting)
        assertTrue(recipes.created.isEmpty())
    }

    @Test
    fun `REQ-MEAL-061 save after import stores it like a manual recipe`() {
        val viewModel = viewModel()

        viewModel.onEvent(RecipeEditEvent.Save)

        val draft = recipes.created.single()
        assertTrue(Ingredient("Vollkornspaghetti", 500.0, "g") in draft.ingredients)
        assertTrue(Ingredient("Salz") in draft.ingredients)
        assertEquals(CARBONARA_URL, draft.sourceUrl)
    }

    @Test
    fun `REQ-MEAL-061 corrected ingredients are saved`() {
        val viewModel = viewModel()

        viewModel.onEvent(RecipeEditEvent.IngredientsChanged("300 g Vollkornspaghetti"))
        viewModel.onEvent(RecipeEditEvent.Save)

        assertEquals(listOf(Ingredient("Vollkornspaghetti", 300.0, "g")), recipes.created.single().ingredients)
    }

    @Test
    fun `REQ-MEAL-061 pasted link imports on demand`() {
        val viewModel = viewModel(RecipeEditArgs())
        assertFalse(viewModel.state.value.canImport)

        viewModel.onEvent(RecipeEditEvent.SourceUrlChanged(CARBONARA_URL))
        viewModel.onEvent(RecipeEditEvent.Import)

        assertEquals(listOf(CARBONARA_URL), source.requested)
        assertEquals(4, viewModel.state.value.servings)
        assertTrue(
            viewModel.state.value.name
                .startsWith("Spaghetti"),
        )
    }

    @Test
    fun `REQ-MEAL-061 while importing saving and importing again are disabled`() {
        source.gate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.onEvent(RecipeEditEvent.IngredientsChanged("Salz"))
        viewModel.onEvent(RecipeEditEvent.NameChanged("Curry"))

        assertTrue(viewModel.state.value.isImporting)
        assertFalse(viewModel.state.value.canSave)
        assertFalse(viewModel.state.value.canImport)
        viewModel.onEvent(RecipeEditEvent.Import)
        viewModel.onEvent(RecipeEditEvent.Save)
        assertEquals(1, source.requested.size)
        assertTrue(recipes.created.isEmpty())

        source.gate?.complete(Unit)
        assertFalse(viewModel.state.value.isImporting)
    }

    @Test
    fun `REQ-MEAL-061 page without recipe keeps the form and reports it`() {
        source.result = SourceResult.NotFound
        val viewModel = viewModel(RecipeEditArgs())
        viewModel.onEvent(RecipeEditEvent.NameChanged("Curry"))
        viewModel.onEvent(RecipeEditEvent.SourceUrlChanged(CARBONARA_URL))

        viewModel.onEvent(RecipeEditEvent.Import)

        assertEquals(RecipeImportError.NO_RECIPE, viewModel.state.value.importError)
        assertEquals("Curry", viewModel.state.value.name)
        assertFalse(viewModel.state.value.isImporting)
    }

    @Test
    fun `REQ-MEAL-061 unreachable page is reported`() {
        source.result = SourceResult.Unreachable

        assertEquals(RecipeImportError.UNREACHABLE, viewModel().state.value.importError)
    }

    @Test
    fun `REQ-MEAL-061 shared text without link is reported`() {
        val state = viewModel(RecipeEditArgs(importText = "kein Link")).state.value

        assertEquals(RecipeImportError.NO_LINK, state.importError)
        assertTrue(source.requested.isEmpty())
    }

    @Test
    fun `REQ-MEAL-061 children cannot import`() {
        sessions = InMemorySessionStore(childSession)

        val state = viewModel().state.value

        assertEquals(AppError.Forbidden, state.loadError)
        assertTrue(source.requested.isEmpty())
    }

    @Test
    fun `REQ-MEAL-061 existing recipe ignores import text`() {
        val viewModel = viewModel(RecipeEditArgs("r-1", shared))

        assertTrue(source.requested.isEmpty())
        assertFalse(viewModel.state.value.canImport)
        assertEquals("Spaghetti mit Lauch-Carbonara", viewModel.state.value.name)
    }

    @Test
    fun `REQ-MEAL-061 editing the link clears the import error`() {
        source.result = SourceResult.NotFound
        val viewModel = viewModel()

        viewModel.onEvent(RecipeEditEvent.SourceUrlChanged("https://www.rewe.de/"))

        assertNull(viewModel.state.value.importError)
    }
}
