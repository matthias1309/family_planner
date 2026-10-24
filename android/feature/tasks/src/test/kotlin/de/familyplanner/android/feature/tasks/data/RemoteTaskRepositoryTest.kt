package de.familyplanner.android.feature.tasks.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.api.client.TasksApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteTaskRepositoryTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteTaskRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        repository = RemoteTaskRepository(retrofit.create(TasksApi::class.java), json)
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

    private fun cardJson(
        id: String = "t-1",
        status: String = "TODO",
        extra: String = "",
    ) = """{"id":"$id","memberId":"c-1","title":"Bad putzen","emoji":"🛁","points":3,"status":"$status",""" +
        """"assignedAt":"2026-10-24T10:00:00Z"$extra}"""

    @Test
    fun `REQ-TASK-021 board maps lanes cards and timestamps`() =
        runTest {
            enqueue(
                200,
                ok(
                    """{"lanes":[{"memberId":"c-1","name":"Ben","cards":[""" +
                        cardJson("t-1", "REVIEW", ""","reportedAt":"2026-10-24T11:00:00Z"""") +
                        """,""" + cardJson("t-2", "TODO", ""","rejectionReason":"Unter dem Bett"""") + """]}]}""",
                ),
            )

            val lanes = (repository.board() as ApiResult.Success).data

            assertEquals("/api/v1/tasks/board", server.takeRequest().url.encodedPath)
            assertEquals("Ben", lanes.single().name)
            val (review, todo) = lanes.single().cards
            assertEquals(TaskStatus.REVIEW, review.status)
            assertEquals(Instant.parse("2026-10-24T11:00:00Z"), review.reportedAt)
            assertEquals("🛁", review.emoji)
            assertEquals("Unter dem Bett", todo.rejectionReason)
            assertNull(todo.doneAt)
        }

    @Test
    fun `REQ-TASK-021 an unreadable timestamp is a server error`() =
        runTest {
            enqueue(
                200,
                ok("""{"lanes":[{"memberId":"c-1","name":"Ben","cards":[${cardJson().replace("2026-10-24T10:00:00Z", "kaputt")}]}]}"""),
            )

            assertEquals(ApiResult.Failure(AppError.Server), repository.board())
        }

    @Test
    fun `REQ-TASK-025 history requests the member path with the limit`() =
        runTest {
            enqueue(200, ok("[" + cardJson("t-9", "DONE", ""","doneAt":"2026-10-23T10:00:00Z"""") + "]"))

            val cards = (repository.history("c-1", 50) as ApiResult.Success).data

            val url = server.takeRequest().url
            assertEquals("/api/v1/tasks/members/c-1/history", url.encodedPath)
            assertEquals("50", url.queryParameter("limit"))
            assertEquals(Instant.parse("2026-10-23T10:00:00Z"), cards.single().doneAt)
        }

    @Test
    fun `REQ-TASK-020 assign posts member and catalog ids`() =
        runTest {
            enqueue(201, ok("[" + cardJson("t-1") + "," + cardJson("t-2") + "]"))

            val cards = (repository.assign("c-1", listOf("k-1", "k-2")) as ApiResult.Success).data

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/tasks/cards", request.url.encodedPath)
            val body = request.body?.utf8().orEmpty()
            assertEquals(true, body.contains(""""memberId":"c-1"""") && body.contains(""""catalogTaskIds":["k-1","k-2"]"""))
            assertEquals(2, cards.size)
        }

    @Test
    fun `REQ-TASK-022 transitions use their own paths`() =
        runTest {
            enqueue(200, ok(cardJson(status = "REVIEW")))
            enqueue(200, ok(cardJson(status = "TODO")))
            enqueue(200, ok(cardJson(status = "DONE")))

            repository.report("t-1")
            repository.withdraw("t-1")
            repository.accept("t-1")

            assertEquals(
                listOf(
                    "POST /api/v1/tasks/cards/t-1/report",
                    "POST /api/v1/tasks/cards/t-1/withdrawal",
                    "POST /api/v1/tasks/cards/t-1/acceptance",
                ),
                (1..3).map { server.takeRequest().let { r -> "${r.method} ${r.url.encodedPath}" } },
            )
        }

    @Test
    fun `REQ-TASK-023 reject always sends a body, with or without a reason`() =
        runTest {
            enqueue(200, ok(cardJson()))
            enqueue(200, ok(cardJson()))

            repository.reject("t-1", "Unter dem Bett")
            repository.reject("t-1", null)

            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("/api/v1/tasks/cards/t-1/rejection", first.url.encodedPath)
            assertEquals("""{"reason":"Unter dem Bett"}""", first.body?.utf8())
            assertEquals("{}", second.body?.utf8())
        }

    @Test
    fun `REQ-TASK-024 remove accepts 204 and maps errors`() =
        runTest {
            enqueue(204)
            enqueue(409, """{"success":false,"error":{"code":"CONFLICT","message":"Die Aufgabe ist schon erledigt"}}""")
            enqueue(403, """{"success":false,"error":{"code":"FORBIDDEN","message":"Nur für Eltern"}}""")

            assertEquals(ApiResult.Success(Unit), repository.remove("t-1"))
            assertEquals("DELETE", server.takeRequest().method)
            assertEquals(ApiResult.Failure(AppError.Conflict("Die Aufgabe ist schon erledigt")), repository.remove("t-1"))
            assertEquals(ApiResult.Failure(AppError.Forbidden), repository.remove("t-1"))
        }
}
