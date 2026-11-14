package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.meals.fakes.FakeRecipeRepository
import de.familyplanner.android.feature.meals.fakes.carbonaraSummary
import de.familyplanner.android.feature.meals.fakes.childSession
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.fakes.quesadilla
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UseCasesTest {
    private val repository = FakeRecipeRepository()
    private val sessions = InMemorySessionStore(parentSession)
    private val cache = RecipeListCache(sessions)
    private val save = SaveRecipeUseCase(repository, cache)

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
            assertEquals(repository.listResult, LoadRecipesUseCase(repository, cache)())
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
            DeleteRecipeUseCase(repository, cache)("r-1")

            assertEquals(listOf("r-1"), repository.deleted)
        }

    // ---- Zuletzt geladene Liste (ADR-0018) ----

    private suspend fun loadIntoCache() = LoadRecipesUseCase(repository, cache)()

    @Test
    fun `REQ-MEAL-060 successful load keeps the list for the next opening`() =
        runTest {
            loadIntoCache()

            assertEquals(listOf(quesadilla, carbonaraSummary), LoadRecipesUseCase(repository, cache).cached())
        }

    @Test
    fun `REQ-MEAL-060 nothing is kept before the first load`() {
        assertNull(LoadRecipesUseCase(repository, cache).cached())
    }

    @Test
    fun `REQ-MEAL-060 failed load keeps the previous list`() =
        runTest {
            loadIntoCache()
            repository.listResult = ApiResult.Failure(AppError.Network)

            loadIntoCache()

            assertEquals(listOf(quesadilla, carbonaraSummary), cache.read())
        }

    @Test
    fun `REQ-MEAL-060 list of another member is not shown`() =
        runTest {
            loadIntoCache()

            sessions.setCurrent(childSession)

            assertNull(cache.read())
        }

    @Test
    fun `REQ-MEAL-060 load after signing out forgets the kept list`() =
        runTest {
            loadIntoCache()
            sessions.setCurrent(null)

            loadIntoCache()
            sessions.setCurrent(parentSession)

            assertNull(cache.read())
        }

    @Test
    fun `REQ-MEAL-060 list loaded for one member is not kept for the next`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.listGate = gate
            val load = async { loadIntoCache() }
            runCurrent()
            sessions.setCurrent(childSession)

            gate.complete(Unit)
            load.await()

            assertNull(cache.read())
            sessions.setCurrent(parentSession)
            assertEquals(listOf(quesadilla, carbonaraSummary), cache.read())
        }

    @Test
    fun `REQ-MEAL-060 list requested before a delete does not bring the recipe back`() =
        runTest {
            loadIntoCache()
            val gate = CompletableDeferred<Unit>()
            repository.listGate = gate
            val load = async { loadIntoCache() }
            runCurrent()

            DeleteRecipeUseCase(repository, cache)("r-1")
            gate.complete(Unit)
            load.await()

            assertEquals(listOf(quesadilla), cache.read())
        }

    @Test
    fun `REQ-MEAL-060 changed recipe is updated in the kept list`() =
        runTest {
            loadIntoCache()

            save("r-1", input(name = "Lauch-Carbonara"))

            assertEquals(listOf(quesadilla, RecipeSummary("r-1", "Lauch-Carbonara", 4)), cache.read())
        }

    @Test
    fun `REQ-MEAL-060 new recipe is added to the kept list`() =
        runTest {
            loadIntoCache()

            save(null, input())

            assertEquals(listOf(quesadilla, carbonaraSummary, RecipeSummary("r-new", "Curry", 4)), cache.read())
        }

    @Test
    fun `REQ-MEAL-060 failed save leaves the kept list unchanged`() =
        runTest {
            loadIntoCache()
            repository.updateResult = ApiResult.Failure(AppError.Network)

            save("r-1", input(name = "Lauch-Carbonara"))

            assertEquals(listOf(quesadilla, carbonaraSummary), cache.read())
        }

    @Test
    fun `REQ-MEAL-060 deleted recipe is removed from the kept list`() =
        runTest {
            loadIntoCache()

            DeleteRecipeUseCase(repository, cache)("r-1")

            assertEquals(listOf(quesadilla), cache.read())
        }

    @Test
    fun `REQ-MEAL-060 recipe already deleted elsewhere is removed from the kept list`() =
        runTest {
            loadIntoCache()
            repository.deleteResult = ApiResult.Failure(AppError.NotFound)

            DeleteRecipeUseCase(repository, cache)("r-1")

            assertEquals(listOf(quesadilla), cache.read())
        }

    @Test
    fun `REQ-MEAL-060 failed delete leaves the kept list unchanged`() =
        runTest {
            loadIntoCache()
            repository.deleteResult = ApiResult.Failure(AppError.Network)

            DeleteRecipeUseCase(repository, cache)("r-1")

            assertEquals(listOf(quesadilla, carbonaraSummary), cache.read())
        }
}
