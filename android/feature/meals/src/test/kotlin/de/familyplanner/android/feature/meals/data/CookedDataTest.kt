package de.familyplanner.android.feature.meals.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.feature.meals.data.cooking.RemoteCookingRepository
import de.familyplanner.android.feature.meals.data.plan.toDomain
import de.familyplanner.android.feature.meals.data.plan.toStored
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import de.familyplanner.android.feature.meals.fakes.childDish
import de.familyplanner.api.client.MealsApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import de.familyplanner.api.model.Dish as ApiDish

class CookedDataTest {
    private val json: Json = createJson()
    private val cookedAt = Instant.parse("2026-10-21T10:00:00Z")

    private fun dishJson(cooked: String) =
        """{"id":"d-2","date":"2026-10-20","cookId":"c-1","name":"Curry","ingredients":["Reis"],"structuredIngredients":[],""" +
            """"status":"APPROVED","late":false,"dayValid":true$cooked}"""

    @Test
    fun `REQ-MEAL-034 api dish maps cookedAt`() {
        val cooked = json.decodeFromString<ApiDish>(dishJson(""","cookedAt":"2026-10-21T10:00:00Z"""")).toDomain()
        val open = json.decodeFromString<ApiDish>(dishJson("")).toDomain()

        assertEquals(cookedAt, cooked?.cookedAt)
        assertNull(open?.cookedAt)
    }

    @Test
    fun `REQ-MEAL-034 unreadable cookedAt is a server error`() {
        assertNull(json.decodeFromString<ApiDish>(dishJson(""","cookedAt":"gestern"""")).toDomain())
    }

    @Test
    fun `REQ-MEAL-034 cached plan keeps cookedAt and the child flag`() {
        val dish = childDish.copy(cookedAt = cookedAt)
        val snapshot = WeekPlanSnapshot(dish.date, listOf(PlannedDay(dish.date, dish, "Ben", cookIsChild = true)), cookedAt)

        val restored = snapshot.toStored().toDomain()

        assertEquals(snapshot, restored)
    }

    @Test
    fun `REQ-MEAL-034 old plan cache files without cookedAt stay readable`() {
        val old =
            """{"weekStart":"2026-10-19","syncedAtMillis":0,"days":[{"date":"2026-10-20","dish":""" +
                """{"id":"d","cookId":"c-1","name":"Curry","ingredients":[]},"cookName":"Ben"}]}"""
        val stored = json.decodeFromString<de.familyplanner.android.feature.meals.data.plan.StoredWeek>(old)

        val day = stored.toDomain()?.days?.single()

        assertNull(day?.dish?.cookedAt)
        assertEquals(false, day?.cookIsChild)
    }

    private fun server(vararg codes: Pair<Int, String>): Pair<MockWebServer, RemoteCookingRepository> {
        val server = MockWebServer().apply { start() }
        codes.forEach { (code, body) ->
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(code)
                    .body(body)
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )
        }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        return server to RemoteCookingRepository(retrofit.create(MealsApi::class.java), json)
    }

    @Test
    fun `REQ-MEAL-034 confirmCooked posts and accepts 204`() =
        runTest {
            val (server, repository) = server(204 to "")

            assertEquals(ApiResult.Success(Unit), repository.confirmCooked("d-2"))
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/meals/dishes/d-2/cooking-confirmation", request.url.encodedPath)
            server.close()
        }

    @Test
    fun `REQ-MEAL-034 confirmCooked maps 409 to conflict with the message`() =
        runTest {
            val (server, repository) = server(409 to """{"success":false,"error":{"code":"CONFLICT","message":"Schon bestätigt"}}""")

            assertEquals(ApiResult.Failure(AppError.Conflict("Schon bestätigt")), repository.confirmCooked("d-2"))
            server.close()
        }
}
