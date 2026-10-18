package de.familyplanner.android.feature.meals.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.feature.meals.data.cooking.RemoteCookingRepository
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.api.client.MealsApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteCookingRepositoryTest {
    private val json = createJson()
    private val monday = LocalDate.of(2026, 10, 19)
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteCookingRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        repository = RemoteCookingRepository(retrofit.create(MealsApi::class.java), json)
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

    private fun statusJson(
        orderDeadline: String = "2026-10-25T14:00:00Z",
        cookDeadline: String = """"2026-10-15T14:00:00Z"""",
    ) = ok(
        """{"weekStart":"2026-10-19","orderDeadline":"$orderDeadline","readyToOrder":false,"openDays":["2026-10-23","2026-10-24"],""" +
            """"cooks":[{"memberId":"c-1","state":"SUBMITTED","late":true,"dayValid":true,""" +
            """"submissionDeadline":$cookDeadline,"dishId":"d-2"},""" +
            """{"memberId":"c-2","state":"CANCELLED","late":false,"dayValid":true,"cancellationReason":"krank"}]}""",
    )

    @Test
    fun `REQ-MEAL-033 status maps open days cooks and ready flag`() =
        runTest {
            enqueue(200, statusJson())

            val status = (repository.status(monday) as ApiResult.Success).data

            assertEquals(listOf(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24)), status.openDays)
            assertEquals(false, status.readyToOrder)
            assertEquals(Instant.parse("2026-10-25T14:00:00Z"), status.orderDeadline)
            assertEquals(listOf(CookState.SUBMITTED, CookState.CANCELLED), status.cooks.map { it.state })
            assertEquals("d-2", status.cooks[0].dishId)
            assertEquals(true, status.cooks[0].late)
            assertEquals("krank", status.cooks[1].cancellationReason)
        }

    @Test
    fun `REQ-MEAL-033 unknown cook state is a server error`() =
        runTest {
            enqueue(200, statusJson().replace("SUBMITTED", "WEIRD"))

            assertEquals(ApiResult.Failure(AppError.Server), repository.status(monday))
        }

    @Test
    fun `REQ-MEAL-033 status requests ISO monday path`() =
        runTest {
            enqueue(200, statusJson())

            repository.status(monday)

            assertEquals("/api/v1/meals/weeks/2026-10-19/status", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-033 unreadable deadline is a server error`() =
        runTest {
            enqueue(200, statusJson(orderDeadline = "kaputt"))

            assertEquals(ApiResult.Failure(AppError.Server), repository.status(monday))
        }

    @Test
    fun `REQ-MEAL-033 cook without deadline maps to null deadline`() =
        runTest {
            enqueue(200, statusJson(cookDeadline = "null"))

            val status = (repository.status(monday) as ApiResult.Success).data

            assertNull(status.cooks[0].submissionDeadline)
        }

    @Test
    fun `REQ-MEAL-030 approve posts to approval path and accepts 204`() =
        runTest {
            enqueue(204)

            val result = repository.approve("d-2")

            assertEquals(ApiResult.Success(Unit), result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/meals/dishes/d-2/approval", request.url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-030 reject posts reason`() =
        runTest {
            enqueue(204)

            val result = repository.reject("d-2", "Bitte mit Gemüse")

            assertEquals(ApiResult.Success(Unit), result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/meals/dishes/d-2/rejection", request.url.encodedPath)
            assertEquals("""{"reason":"Bitte mit Gemüse"}""", request.body!!.utf8())
        }

    @Test
    fun `REQ-MEAL-030 approving a reviewed dish maps 409 with server message`() =
        runTest {
            enqueue(409, error("CONFLICT", "Das Gericht wartet nicht auf Freigabe"))

            assertEquals(ApiResult.Failure(AppError.Conflict("Das Gericht wartet nicht auf Freigabe")), repository.approve("d-2"))
        }

    @Test
    fun `REQ-MEAL-023 cancel puts reason to cancellation path`() =
        runTest {
            enqueue(200, ok("""{"memberId":"c-1","weekStart":"2026-10-19","reason":"krank"}"""))

            val result = repository.cancel(monday, "c-1", "krank")

            assertEquals(ApiResult.Success(Unit), result)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/meals/weeks/2026-10-19/cancellations/c-1", request.url.encodedPath)
            assertEquals("""{"reason":"krank"}""", request.body!!.utf8())
        }

    @Test
    fun `REQ-MEAL-023 withdraw deletes cancellation and accepts 204`() =
        runTest {
            enqueue(204)

            val result = repository.withdraw(monday, "c-1")

            assertEquals(ApiResult.Success(Unit), result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/meals/weeks/2026-10-19/cancellations/c-1", request.url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-023 withdraw without cancellation maps 404`() =
        runTest {
            enqueue(404, error("NOT_FOUND", "Keine Absage vorhanden"))

            assertEquals(ApiResult.Failure(AppError.NotFound), repository.withdraw(monday, "c-1"))
        }

    @Test
    fun `REQ-MEAL-023 child cancelling for a sibling receives forbidden`() =
        runTest {
            enqueue(403, error("FORBIDDEN", "Nicht erlaubt"))

            assertEquals(ApiResult.Failure(AppError.Forbidden), repository.cancel(monday, "c-2", "krank"))
        }
}
