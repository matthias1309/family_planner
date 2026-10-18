package de.familyplanner.android.feature.meals.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.feature.meals.data.plan.RemoteMealPlanRepository
import de.familyplanner.android.feature.meals.domain.plan.DishDraft
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.domain.recipe.RecipeRef
import de.familyplanner.api.client.MealsApi
import de.familyplanner.api.client.MembersApi
import de.familyplanner.api.model.SaveDishRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import de.familyplanner.api.model.Ingredient as ApiIngredient

class RemoteMealPlanRepositoryTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteMealPlanRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        repository = RemoteMealPlanRepository(retrofit.create(MealsApi::class.java), retrofit.create(MembersApi::class.java), json)
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

    private val dishJson =
        """{"id":"d-1","date":"2026-10-12","cookId":"p-1","name":"Spaghetti","ingredients":["Nudeln","Hackfleisch"],""" +
            """"structuredIngredients":[{"name":"Nudeln"},{"name":"Hackfleisch"}],""" +
            """"status":"APPROVED","late":false,"dayValid":true}"""

    private fun weekJson(
        days: Int = 7,
        firstDate: String = "2026-10-12",
    ) = (0 until days).joinToString(",", "[", "]") { index ->
        val date = if (index == 0) firstDate else "2026-10-${12 + index}"
        """{"date":"$date","dish":${if (index == 0) dishJson else "null"}}"""
    }

    private val draft =
        DishDraft(LocalDate.of(2026, 10, 13), "p-1", "Curry", listOf(Ingredient("Reis"), Ingredient("Erbsen", 2.0, "Dose(n)")))

    @Test
    fun `REQ-MEAL-001 week plan maps seven days with dish or null`() =
        runTest {
            enqueue(200, ok("""{"weekStart":"2026-10-12","days":${weekJson()}}"""))

            val plan = (repository.week(LocalDate.of(2026, 10, 12)) as ApiResult.Success).data

            assertEquals(LocalDate.of(2026, 10, 12), plan.weekStart)
            assertEquals(7, plan.days.size)
            assertEquals(
                "Spaghetti",
                plan.days
                    .first()
                    .dish
                    ?.name,
            )
            assertEquals(
                listOf("Nudeln", "Hackfleisch"),
                plan.days
                    .first()
                    .dish
                    ?.ingredients,
            )
            assertNull(plan.days[1].dish)
        }

    private fun singleDayWeek(dish: String) = ok("""{"weekStart":"2026-10-12","days":${weekJson().replace(dishJson, dish)}}""")

    @Test
    fun `REQ-MEAL-020 dish maps status late and day validity`() =
        runTest {
            enqueue(200, singleDayWeek(dishJson.replace("APPROVED", "SUBMITTED").replace("\"late\":false", "\"late\":true")))

            val dish =
                (repository.week(LocalDate.of(2026, 10, 12)) as ApiResult.Success)
                    .data.days
                    .first()
                    .dish!!

            assertEquals(DishStatus.SUBMITTED, dish.status)
            assertEquals(true, dish.late)
            assertEquals(true, dish.dayValid)
        }

    @Test
    fun `REQ-MEAL-020 unknown dish status is a server error`() =
        runTest {
            enqueue(200, singleDayWeek(dishJson.replace("APPROVED", "WEIRD")))

            assertEquals(ApiResult.Failure(AppError.Server), repository.week(LocalDate.of(2026, 10, 12)))
        }

    @Test
    fun `REQ-MEAL-030 rejected dish maps reason`() =
        runTest {
            enqueue(200, singleDayWeek(dishJson.replace("APPROVED", "REJECTED").replace("}", ""","rejectionReason":"Bitte mit Gemüse"}""")))

            val dish =
                (repository.week(LocalDate.of(2026, 10, 12)) as ApiResult.Success)
                    .data.days
                    .first()
                    .dish!!

            assertEquals(DishStatus.REJECTED, dish.status)
            assertEquals("Bitte mit Gemüse", dish.rejectionReason)
        }

    @Test
    fun `REQ-MEAL-021 invalid day maps dayValid false`() =
        runTest {
            enqueue(200, singleDayWeek(dishJson.replace("\"dayValid\":true", "\"dayValid\":false")))

            val dish =
                (repository.week(LocalDate.of(2026, 10, 12)) as ApiResult.Success)
                    .data.days
                    .first()
                    .dish!!

            assertEquals(false, dish.dayValid)
        }

    @Test
    fun `REQ-MEAL-001 week plan requests ISO monday path`() =
        runTest {
            enqueue(200, ok("""{"weekStart":"2026-10-12","days":${weekJson()}}"""))

            repository.week(LocalDate.of(2026, 10, 12))

            assertEquals("/api/v1/meals/weeks/2026-10-12/plan", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-001 week with six days is a server error`() =
        runTest {
            enqueue(200, ok("""{"weekStart":"2026-10-12","days":${weekJson(days = 6)}}"""))

            assertEquals(ApiResult.Failure(AppError.Server), repository.week(LocalDate.of(2026, 10, 12)))
        }

    @Test
    fun `REQ-MEAL-001 unreadable dish date is a server error`() =
        runTest {
            val broken = dishJson.replace("2026-10-12", "kaputt")
            enqueue(200, ok("""{"weekStart":"2026-10-12","days":[{"date":"2026-10-12","dish":$broken}]}"""))

            assertEquals(ApiResult.Failure(AppError.Server), repository.week(LocalDate.of(2026, 10, 12)))
        }

    @Test
    fun `REQ-MEAL-032 members map parent flag`() =
        runTest {
            enqueue(
                200,
                ok(
                    """[{"id":"p-1","name":"Anna","role":"PARENT","createdAt":"2026-10-01T10:00:00Z"},
                    {"id":"c-1","name":"Ben","role":"CHILD","createdAt":"2026-10-01T10:00:00Z"}]""",
                ),
            )

            val members = (repository.members() as ApiResult.Success).data

            assertEquals(listOf(true, false), members.map { it.isParent })
            assertEquals(listOf("Anna", "Ben"), members.map { it.name })
        }

    @Test
    fun `REQ-MEAL-062 create sends structured ingredients and empty lines`() =
        runTest {
            enqueue(201, ok(dishJson))

            val result = repository.createDish(draft)

            assertEquals("Spaghetti", (result as ApiResult.Success).data.name)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/meals/dishes", request.url.encodedPath)
            val body = json.decodeFromString<SaveDishRequest>(request.body!!.utf8())
            assertEquals(
                SaveDishRequest(
                    "2026-10-13",
                    "p-1",
                    "Curry",
                    emptyList(),
                    listOf(ApiIngredient("Reis"), ApiIngredient("Erbsen", 2.0, "Dose(n)")),
                ),
                body,
            )
        }

    @Test
    fun `REQ-MEAL-062 dish without recipe sends no recipe fields`() =
        runTest {
            enqueue(201, ok(dishJson))

            repository.createDish(draft)

            val body = json.decodeFromString<SaveDishRequest>(server.takeRequest().body!!.utf8())
            assertNull(body.recipeId)
            assertNull(body.servings)
        }

    @Test
    fun `REQ-MEAL-062 create sends recipe reference`() =
        runTest {
            enqueue(201, ok(dishJson))

            repository.createDish(draft.copy(recipe = RecipeRef("r-1", 5)))

            val body = json.decodeFromString<SaveDishRequest>(server.takeRequest().body!!.utf8())
            assertEquals("r-1", body.recipeId)
            assertEquals(5, body.servings)
        }

    @Test
    fun `REQ-MEAL-062 dish maps recipe reference`() =
        runTest {
            val withRecipe = dishJson.replace(""""status":""", """"recipeId":"r-1","servings":5,"status":""")
            enqueue(200, ok("""{"weekStart":"2026-10-12","days":${weekJson().replace(dishJson, withRecipe)}}"""))

            val dish =
                (repository.week(LocalDate.of(2026, 10, 12)) as ApiResult.Success)
                    .data.days
                    .first()
                    .dish!!

            assertEquals("r-1", dish.recipeId)
            assertEquals(5, dish.servings)
        }

    @Test
    fun `REQ-MEAL-062 dish without recipe maps nulls`() =
        runTest {
            enqueue(200, ok("""{"weekStart":"2026-10-12","days":${weekJson()}}"""))

            val dish =
                (repository.week(LocalDate.of(2026, 10, 12)) as ApiResult.Success)
                    .data.days
                    .first()
                    .dish!!

            assertNull(dish.recipeId)
            assertNull(dish.servings)
        }

    @Test
    fun `REQ-MEAL-032 update dish uses dish path and PUT`() =
        runTest {
            enqueue(200, ok(dishJson))

            repository.updateDish("d-1", draft)

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/meals/dishes/d-1", request.url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-032 delete dish uses dish path and 204`() =
        runTest {
            enqueue(204)

            val result = repository.deleteDish("d-1")

            assertEquals(ApiResult.Success(Unit), result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/meals/dishes/d-1", request.url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-032 taken day maps 409 to conflict with server message`() =
        runTest {
            enqueue(409, error("CONFLICT", "An diesem Tag ist schon ein Gericht geplant"))

            val result = repository.createDish(draft)

            assertEquals(ApiResult.Failure(AppError.Conflict("An diesem Tag ist schon ein Gericht geplant")), result)
        }

    @Test
    fun `REQ-MEAL-032 child receives forbidden on create`() =
        runTest {
            enqueue(403, error("FORBIDDEN", "Nur für Eltern."))

            assertEquals(ApiResult.Failure(AppError.Forbidden), repository.createDish(draft))
        }

    @Test
    fun `REQ-MEAL-032 validation error keeps server message`() =
        runTest {
            enqueue(400, error("VALIDATION_FAILED", "Der Name ist zu lang."))

            assertEquals(ApiResult.Failure(AppError.Validation("Der Name ist zu lang.")), repository.createDish(draft))
        }

    @Test
    fun `network failure maps to Network`() =
        runTest {
            server.close()

            assertEquals(ApiResult.Failure(AppError.Network), repository.week(LocalDate.of(2026, 10, 12)))
        }
}
