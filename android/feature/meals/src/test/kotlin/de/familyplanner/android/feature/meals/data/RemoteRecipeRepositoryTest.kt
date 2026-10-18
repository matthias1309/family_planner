package de.familyplanner.android.feature.meals.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.feature.meals.data.recipe.RemoteRecipeRepository
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.domain.recipe.RecipeDraft
import de.familyplanner.api.client.MealsApi
import de.familyplanner.api.model.SaveRecipeRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import de.familyplanner.api.model.Ingredient as ApiIngredient

class RemoteRecipeRepositoryTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteRecipeRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        repository = RemoteRecipeRepository(retrofit.create(MealsApi::class.java), json)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(
        code: Int,
        body: String = "",
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .addHeader("Content-Type", "application/json")
            .build(),
    )

    private fun ok(data: String) = """{"success":true,"data":$data}"""

    private fun error(
        code: String,
        message: String,
    ) = """{"success":false,"error":{"code":"$code","message":"$message"}}"""

    private fun recipeJson(servings: Int = 4) =
        """{"id":"r-1","name":"Carbonara","servings":$servings,"ingredients":[{"name":"Spaghetti","quantity":500.0,"unit":"g"},""" +
            """{"name":"Salz"}],""" +
            """"steps":["Kochen."],"createdBy":"p-1","sourceUrl":"https://www.rewe.de/rezepte/x/"}"""

    private val draft =
        RecipeDraft("Curry", 4, listOf(Ingredient("Reis", 200.0, "g"), Ingredient("Salz")), listOf("Kochen."), null)

    @Test
    fun `REQ-MEAL-060 list maps summaries without query`() =
        runTest {
            enqueue(200, ok("""[{"id":"r-1","name":"Carbonara","servings":4},{"id":"r-2","name":"Quesadilla","servings":2}]"""))

            val recipes = (repository.list() as ApiResult.Success).data

            assertEquals(listOf("Carbonara", "Quesadilla"), recipes.map { it.name })
            assertEquals(listOf(4, 2), recipes.map { it.servings })
            val url = server.takeRequest().url
            assertEquals("/api/v1/meals/recipes", url.encodedPath)
            assertNull(url.encodedQuery)
        }

    @Test
    fun `REQ-MEAL-060 recipe maps ingredients steps and source`() =
        runTest {
            enqueue(200, ok(recipeJson()))

            val recipe = (repository.recipe("r-1") as ApiResult.Success).data

            assertEquals(listOf(Ingredient("Spaghetti", 500.0, "g"), Ingredient("Salz")), recipe.ingredients)
            assertEquals(listOf("Kochen."), recipe.steps)
            assertEquals("https://www.rewe.de/rezepte/x/", recipe.sourceUrl)
            assertEquals("/api/v1/meals/recipes/r-1", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-060 create posts structured ingredients and steps`() =
        runTest {
            enqueue(201, ok(recipeJson()))

            repository.create(draft)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/meals/recipes", request.url.encodedPath)
            val body = json.decodeFromString<SaveRecipeRequest>(request.body!!.utf8())
            assertEquals(
                SaveRecipeRequest("Curry", 4, listOf(ApiIngredient("Reis", 200.0, "g"), ApiIngredient("Salz")), listOf("Kochen.")),
                body,
            )
        }

    @Test
    fun `REQ-MEAL-060 duplicate name maps conflict with server message`() =
        runTest {
            enqueue(409, error("CONFLICT", "Ein Rezept mit diesem Namen gibt es schon"))

            assertEquals(
                ApiResult.Failure(AppError.Conflict("Ein Rezept mit diesem Namen gibt es schon")),
                repository.create(draft),
            )
        }

    @Test
    fun `REQ-MEAL-060 child create receives forbidden`() =
        runTest {
            enqueue(403, error("FORBIDDEN", "Nur für Eltern."))

            assertEquals(ApiResult.Failure(AppError.Forbidden), repository.create(draft))
        }

    @Test
    fun `REQ-MEAL-060 update puts to recipe path`() =
        runTest {
            enqueue(200, ok(recipeJson()))

            repository.update("r-1", draft)

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/meals/recipes/r-1", request.url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-060 delete accepts 204`() =
        runTest {
            enqueue(204)

            assertEquals(ApiResult.Success(Unit), repository.delete("r-1"))
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/meals/recipes/r-1", request.url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-060 unknown recipe maps not found`() =
        runTest {
            enqueue(404, error("NOT_FOUND", "Rezept nicht gefunden"))

            assertEquals(ApiResult.Failure(AppError.NotFound), repository.recipe("r-9"))
        }

    @Test
    fun `REQ-MEAL-060 recipe with zero servings is a server error`() =
        runTest {
            enqueue(200, ok(recipeJson(servings = 0)))

            assertEquals(ApiResult.Failure(AppError.Server), repository.recipe("r-1"))
        }

    @Test
    fun `REQ-MEAL-060 summary with zero servings is a server error`() =
        runTest {
            enqueue(200, ok("""[{"id":"r-1","name":"Carbonara","servings":0}]"""))

            assertEquals(ApiResult.Failure(AppError.Server), repository.list())
        }

    @Test
    fun `REQ-MEAL-060 network failure maps to Network`() =
        runTest {
            server.close()

            assertEquals(ApiResult.Failure(AppError.Network), repository.list())
        }
}
