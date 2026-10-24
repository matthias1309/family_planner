package de.familyplanner.android.feature.meals.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.feature.meals.data.shopping.RemoteShoppingRepository
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.api.client.MealsApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteShoppingRepositoryTest {
    private val json = createJson()
    private val monday = LocalDate.of(2026, 10, 19)
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteShoppingRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        repository = RemoteShoppingRepository(retrofit.create(MealsApi::class.java), json)
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

    private fun listJson(date: String = "2026-10-20") =
        ok(
            """{"weekStart":"2026-10-19","entries":[""" +
                """{"key":"k-1","name":"Vollkornspaghetti","quantity":625.0,"unit":"g","checked":false,""" +
                """"sources":[{"dishId":"d-1","dishName":"Carbonara","date":"$date"}]},""" +
                """{"key":"k-2","name":"Salz","checked":true,"sources":[]},""" +
                """{"key":"free:i-1","name":"Kaffee","quantity":1.0,"unit":"Packung","checked":false,"itemId":"i-1","sources":[]}]}""",
        )

    @Test
    fun `REQ-MEAL-040 repository maps entries sources and checks`() =
        runTest {
            enqueue(200, listJson())

            val list = (repository.list(monday) as ApiResult.Success).data

            assertEquals(monday, list.weekStart)
            assertEquals(3, list.entries.size)
            val first = list.entries[0]
            assertEquals(625.0, first.quantity)
            assertEquals("g", first.unit)
            assertEquals(LocalDate.of(2026, 10, 20), first.sources.single().date)
            assertEquals("Carbonara", first.sources.single().dishName)
            assertNull(list.entries[1].quantity)
            assertNull(list.entries[1].unit)
            assertEquals(true, list.entries[1].checked)
            assertEquals("i-1", list.entries[2].itemId)
            assertEquals("/api/v1/meals/weeks/2026-10-19/shopping-list", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-040 unreadable source date is a server error`() =
        runTest {
            enqueue(200, listJson(date = "x"))

            assertEquals(ApiResult.Failure(AppError.Server), repository.list(monday))
        }

    @Test
    fun `REQ-MEAL-041 add item posts ingredient and returns list`() =
        runTest {
            enqueue(201, listJson())

            val result = repository.addItem(monday, Ingredient("Kartoffeln", 2.0, "kg"))

            assertEquals(3, (result as ApiResult.Success).data.entries.size)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/meals/weeks/2026-10-19/shopping-list/items", request.url.encodedPath)
            assertEquals("""{"name":"Kartoffeln","quantity":2.0,"unit":"kg"}""", request.body?.utf8())
        }

    @Test
    fun `REQ-MEAL-041 check puts key and flag`() =
        runTest {
            enqueue(200, listJson())

            repository.setChecked(monday, "k-1", true)

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/meals/weeks/2026-10-19/shopping-list/checks", request.url.encodedPath)
            assertEquals("""{"key":"k-1","checked":true}""", request.body?.utf8())
        }

    @Test
    fun `REQ-MEAL-041 delete without content succeeds`() =
        runTest {
            enqueue(204)

            assertEquals(ApiResult.Success(Unit), repository.deleteItem(monday, "i-1"))
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/meals/weeks/2026-10-19/shopping-list/items/i-1", request.url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-041 child write is forbidden`() =
        runTest {
            enqueue(403, error("FORBIDDEN", "Nur Eltern"))

            assertEquals(ApiResult.Failure(AppError.Forbidden), repository.setChecked(monday, "k-1", true))
        }

    @Test
    fun `REQ-MEAL-041 server limit message is shown`() =
        runTest {
            enqueue(400, error("VALIDATION_FAILED", "Höchstens 50 freie Einträge"))

            val result = repository.addItem(monday, Ingredient("Brot"))

            assertEquals(ApiResult.Failure(AppError.Validation("Höchstens 50 freie Einträge")), result)
        }

    @Test
    fun `REQ-MEAL-041 unknown entry is not found`() =
        runTest {
            enqueue(404, error("NOT_FOUND", "weg"))

            assertEquals(ApiResult.Failure(AppError.NotFound), repository.setChecked(monday, "x", true))
        }
}
