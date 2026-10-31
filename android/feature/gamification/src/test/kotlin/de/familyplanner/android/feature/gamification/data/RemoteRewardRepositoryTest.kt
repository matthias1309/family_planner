package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import de.familyplanner.api.client.GamificationApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RemoteRewardRepositoryTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteRewardRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        repository = RemoteRewardRepository(retrofit.create(GamificationApi::class.java), json)
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

    private val rewardJson = """{"id":"r-1","title":"Freizeitpark","emoji":"🎢","price":200,"archived":false}"""

    private fun redemptionJson(
        status: String = "PENDING",
        decidedAt: String? = null,
    ) = """{"id":"d-1","memberId":"c-1","rewardId":"r-1","title":"Freizeitpark","emoji":"🎢","price":200,"status":"$status",""" +
        """"requestedAt":"2026-10-25T10:00:00Z","decidedAt":${decidedAt?.let { "\"$it\"" }},"rejectionReason":null}"""

    @Test
    fun `REQ-GAME-040 rewards are mapped and archived ones are excluded`() =
        runTest {
            enqueue(200, ok("[$rewardJson]"))

            val result = repository.rewards()

            assertEquals("Freizeitpark", (result as ApiResult.Success).data.single().title)
            assertEquals("includeArchived=false", server.takeRequest().url.query)
        }

    @Test
    fun `REQ-GAME-042 redemption keeps status and dates`() =
        runTest {
            enqueue(200, ok(redemptionJson("FULFILLED", "2026-10-25T11:00:00Z")))

            val redemption = (repository.fulfill("d-1") as ApiResult.Success).data

            assertEquals(RedemptionStatus.FULFILLED, redemption.status)
            assertEquals("2026-10-25T11:00:00Z", redemption.decidedAt.toString())
        }

    @Test
    fun `REQ-GAME-043 decided redemption without date is a server error`() =
        runTest {
            enqueue(200, ok(redemptionJson("REJECTED", null)))

            assertEquals(AppError.Server, (repository.reject("d-1", "Ferien") as ApiResult.Failure).error)
        }

    @Test
    fun `REQ-GAME-042 conflict on redeem carries the server message`() =
        runTest {
            enqueue(409, """{"success":false,"error":{"code":"CONFLICT","message":"Guthaben reicht nicht"}}""")

            val error = (repository.redeem("r-1") as ApiResult.Failure).error

            assertEquals(AppError.Conflict("Guthaben reicht nicht"), error)
        }

    @Test
    fun `REQ-GAME-040 archive and clear goal accept 204`() =
        runTest {
            enqueue(204)
            enqueue(204)

            assertEquals(ApiResult.Success(Unit), repository.archive("r-1"))
            assertEquals(ApiResult.Success(Unit), repository.clearSavingsGoal("c-1"))
        }

    @Test
    fun `REQ-GAME-041 savings goal without reward has no progress`() =
        runTest {
            enqueue(200, ok("""{"memberId":"c-1","balance":120,"redeemable":false,"reward":null,"progress":null}"""))

            val goal = (repository.savingsGoal("c-1") as ApiResult.Success).data

            assertNull(goal.reward)
            assertNull(goal.progress)
            assertEquals(120, goal.balance)
        }

    @Test
    fun `REQ-GAME-041 savings goal maps reward and progress`() =
        runTest {
            enqueue(
                200,
                ok("""{"memberId":"c-1","balance":120,"redeemable":false,"reward":$rewardJson,"progress":{"current":120,"target":200}}"""),
            )

            val goal = (repository.setSavingsGoal("c-1", "r-1") as ApiResult.Success).data

            assertEquals("r-1", goal.reward?.id)
            assertEquals(200, goal.progress?.target)
        }

    @Test
    fun `REQ-GAME-043 redemptions are requested with status and limit`() =
        runTest {
            enqueue(200, ok("[]"))

            assertIs<ApiResult.Success<*>>(repository.redemptions(RedemptionStatus.PENDING, 100))
            assertEquals("status=PENDING&limit=100", server.takeRequest().url.query)
        }
}
