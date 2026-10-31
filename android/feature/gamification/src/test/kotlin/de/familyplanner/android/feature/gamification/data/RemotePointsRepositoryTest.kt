package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.feature.gamification.domain.BadgeOverview
import de.familyplanner.android.feature.gamification.domain.EntryKind
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

class RemotePointsRepositoryTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemotePointsRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        repository = RemotePointsRepository(retrofit.create(GamificationApi::class.java), json)
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

    private fun entryJson(
        kind: String = "TASK",
        bookedAt: String = "2026-10-25T10:00:00Z",
    ) = """{"id":"e-1","kind":"$kind","amount":3,"title":"Bad putzen","note":null,"bookedAt":"$bookedAt"}"""

    private fun accountJson(entry: String = entryJson()) = """{"memberId":"c-1","collected":42,"balance":40,"items":[$entry]}"""

    @Test
    fun `REQ-GAME-014 leaderboard maps ranks`() =
        runTest {
            enqueue(200, ok("""{"items":[{"memberId":"c-1","name":"Lea","rank":1,"collected":5}]}"""))

            val result = assertIs<ApiResult.Success<*>>(repository.leaderboard())

            assertEquals(
                "Lea",
                (result.data as List<*>).filterIsInstance<de.familyplanner.android.feature.gamification.domain.RankedChild>().first().name,
            )
            assertEquals("/api/v1/gamification/leaderboard", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `REQ-GAME-012 account requests the limit and maps entries`() =
        runTest {
            enqueue(200, ok(accountJson()))

            val account =
                assertIs<ApiResult.Success<de.familyplanner.android.feature.gamification.domain.PointsAccount>>(
                    repository.account("c-1", 50),
                ).data

            assertEquals(40, account.balance)
            assertEquals(EntryKind.TASK, account.entries.single().kind)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("limit=50", request.url.encodedQuery)
            assertEquals("/api/v1/gamification/members/c-1/account", request.url.encodedPath)
        }

    @Test
    fun `REQ-GAME-012 unknown kind or unreadable date is a server error`() =
        runTest {
            enqueue(200, ok(accountJson(entryJson(kind = "TASK", bookedAt = "gestern"))))

            assertEquals(AppError.Server, assertIs<ApiResult.Failure>(repository.account("c-1", 50)).error)
        }

    @Test
    fun `REQ-GAME-013 book sends the signed amount and accepts 201`() =
        runTest {
            enqueue(201, ok(entryJson("MANUAL")))

            val result = repository.book("c-1", -2, "Fehler")

            assertIs<ApiResult.Success<*>>(result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("""{"amount":-2,"reason":"Fehler"}""", request.body?.utf8())
        }

    @Test
    fun `REQ-GAME-013 booking as a child is forbidden`() =
        runTest {
            enqueue(403, """{"success":false,"error":{"code":"FORBIDDEN","message":"nein"}}""")

            assertEquals(AppError.Forbidden, assertIs<ApiResult.Failure>(repository.book("c-1", 1, "x")).error)
        }

    @Test
    fun `REQ-GAME-011 cooking points are read and updated`() =
        runTest {
            enqueue(200, ok("""{"cookingPoints":3}"""))
            enqueue(200, ok("""{"cookingPoints":4}"""))

            assertEquals(ApiResult.Success(3), repository.cookingPoints())
            assertEquals(ApiResult.Success(4), repository.updateCookingPoints(4))
            server.takeRequest()
            val put = server.takeRequest()
            assertEquals("""{"cookingPoints":4}""", put.body?.utf8())
        }

    private fun badgesJson(vararg items: String) =
        """{"memberId":"c-1","earnedCount":1,"totalCount":12,"items":[${items.joinToString(",")}]}"""

    private val earnedJson =
        """{"id":"first-task","secret":false,"earned":true,"emoji":"🧹","title":"Erste Aufgabe","description":"d","hint":null,""" +
            """"awardedAt":"2026-10-25T10:00:00Z","progress":null}"""
    private val openJson =
        """{"id":"tasks-10","secret":false,"earned":false,"emoji":"✅","title":"Fleißig","description":"d","hint":null,""" +
            """"awardedAt":null,"progress":{"current":7,"target":10}}"""
    private val maskedJson =
        """{"id":"secret-1","secret":true,"earned":false,"emoji":null,"title":null,"description":null,"hint":"Bleib dran",""" +
            """"awardedAt":null,"progress":null}"""

    @Test
    fun `REQ-GAME-021 badges map earned, open and masked badges`() =
        runTest {
            enqueue(200, ok(badgesJson(earnedJson, openJson, maskedJson)))

            val overview = assertIs<ApiResult.Success<BadgeOverview>>(repository.badges("c-1")).data

            assertEquals(3, overview.items.size)
            assertEquals(12, overview.totalCount)
            assertEquals(true, overview.items[0].earned)
            assertEquals(7, overview.items[1].progress?.current)
            assertEquals(true, overview.items[2].masked)
            assertEquals("Bleib dran", overview.items[2].hint)
            assertEquals("/api/v1/gamification/members/c-1/badges", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `REQ-GAME-021 earned badge without date is a server error`() =
        runTest {
            enqueue(200, ok(badgesJson(earnedJson.replace(""""awardedAt":"2026-10-25T10:00:00Z"""", """"awardedAt":null"""))))

            assertEquals(AppError.Server, assertIs<ApiResult.Failure>(repository.badges("c-1")).error)
        }
}
