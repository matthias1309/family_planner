package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.meals.fakes.FakeRecipeRepository
import de.familyplanner.android.feature.meals.fakes.carbonaraSummary
import de.familyplanner.android.feature.meals.fakes.quesadilla
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UseCasesTest {
    private val repository = FakeRecipeRepository()
    private val save = SaveRecipeUseCase(repository)

    private fun input(
        name: String = "Curry",
        ingredients: String = "200 g Reis\nSalz",
        steps: String = "",
        url: String = "",
    ) = RecipeInput(name, 4, ingredients, steps, url)

    @Test
    fun `REQ-MEAL-060 search filters by name ignoring case`() {
        val all = listOf(quesadilla, carbonaraSummary)

        assertEquals(listOf(carbonaraSummary), filterRecipes(all, "  LAUCH "))
        assertEquals(listOf(quesadilla), filterRecipes(all, "quesa"))
        assertTrue(filterRecipes(all, "xyz").isEmpty())
    }

    @Test
    fun `REQ-MEAL-060 blank search shows all`() {
        val all = listOf(quesadilla, carbonaraSummary)

        assertEquals(all, filterRecipes(all, "   "))
    }

    @Test
    fun `REQ-MEAL-060 list is loaded from the repository`() =
        runTest {
            assertEquals(repository.listResult, LoadRecipesUseCase(repository)())
        }

    @Test
    fun `REQ-MEAL-060 save parses ingredient lines`() =
        runTest {
            save(null, input())

            assertEquals(listOf(Ingredient("Reis", 200.0, "g"), Ingredient("Salz")), repository.created.single().ingredients)
        }

    @Test
    fun `REQ-MEAL-060 save trims steps and drops blank lines`() =
        runTest {
            save(null, input(steps = " Kochen. \n\n Essen.  "))

            assertEquals(listOf("Kochen.", "Essen."), repository.created.single().steps)
        }

    @Test
    fun `REQ-MEAL-060 empty source becomes null`() =
        runTest {
            save(null, input(url = "  "))

            assertNull(repository.created.single().sourceUrl)
        }

    @Test
    fun `REQ-MEAL-060 name is trimmed and source kept`() =
        runTest {
            save(null, input(name = "  Curry ", url = " https://www.rewe.de/rezepte/x/ "))

            val draft = repository.created.single()
            assertEquals("Curry", draft.name)
            assertEquals("https://www.rewe.de/rezepte/x/", draft.sourceUrl)
        }

    @Test
    fun `REQ-MEAL-060 invalid recipe sends no request`() =
        runTest {
            val result = save(null, input(name = " "))

            assertIs<AppError.Validation>((result as ApiResult.Failure).error)
            assertTrue(repository.created.isEmpty())
            assertTrue(repository.updated.isEmpty())
        }

    @Test
    fun `REQ-MEAL-060 existing recipe is updated`() =
        runTest {
            save("r-1", input())

            assertEquals("r-1", repository.updated.single().first)
            assertTrue(repository.created.isEmpty())
        }

    @Test
    fun `REQ-MEAL-060 delete passes the recipe id`() =
        runTest {
            DeleteRecipeUseCase(repository)("r-1")

            assertEquals(listOf("r-1"), repository.deleted)
        }
}
