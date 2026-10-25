package de.familyplanner.app

import de.familyplanner.api.model.CatalogTaskResponse
import de.familyplanner.api.model.GameSettingsResponse
import de.familyplanner.api.model.LeaderboardResponse
import de.familyplanner.api.model.LedgerEntryKind
import de.familyplanner.api.model.PointsAccount
import de.familyplanner.api.model.PointsAccountResponse
import de.familyplanner.api.model.TaskCardListResponse
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

private const val GAME = "/api/v1/gamification"
private const val COOKED_DAY = "2026-10-13"

class GamificationApiTest {
    @TempDir
    lateinit var dir: Path

    private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }

    private suspend fun HttpClient.accountOf(
        token: String,
        memberId: String,
    ): PointsAccount {
        val response = call(HttpMethod.Get, "$GAME/members/$memberId/account", token)
        assertEquals(HttpStatusCode.OK, response.status)
        return checkNotNull(json.decodeFromString<PointsAccountResponse>(response.bodyAsText()).data)
    }

    private suspend fun HttpClient.acceptTask(
        family: Family,
        points: Int,
    ) {
        val catalogBody = """{"title":"Bad putzen","points":$points}"""
        val catalog =
            checkNotNull(
                json
                    .decodeFromString<CatalogTaskResponse>(
                        call(HttpMethod.Post, "/api/v1/tasks/catalog", family.parentToken, catalogBody).bodyAsText(),
                    ).data,
            )
        val assignBody = """{"memberId":"${family.childId}","catalogTaskIds":["${catalog.id}"]}"""
        val cards =
            checkNotNull(
                json
                    .decodeFromString<TaskCardListResponse>(
                        call(HttpMethod.Post, "/api/v1/tasks/cards", family.parentToken, assignBody).bodyAsText(),
                    ).data,
            )
        val cardId = cards.single().id
        assertEquals(HttpStatusCode.OK, call(HttpMethod.Post, "/api/v1/tasks/cards/$cardId/report", family.childToken).status)
        assertEquals(HttpStatusCode.OK, call(HttpMethod.Post, "/api/v1/tasks/cards/$cardId/acceptance", family.parentToken).status)
    }

    private suspend fun HttpClient.book(
        token: String,
        memberId: String,
        amount: Int,
        reason: String = "Korrektur",
    ): HttpResponse = call(HttpMethod.Post, "$GAME/members/$memberId/entries", token, """{"amount":$amount,"reason":"$reason"}""")

    @Test
    fun `REQ-GAME-010 accepted task shows up in account and leaderboard`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            client.acceptTask(family, 2)

            val account = client.accountOf(family.childToken, family.childId)
            assertEquals(2, account.collected)
            assertEquals(2, account.balance)
            assertEquals(listOf(LedgerEntryKind.TASK), account.items.map { it.kind })
            val board =
                checkNotNull(
                    json
                        .decodeFromString<LeaderboardResponse>(
                            client.call(HttpMethod.Get, "$GAME/leaderboard", family.childToken).bodyAsText(),
                        ).data,
                )
            assertEquals(listOf(1), board.items.map { it.rank })
            assertEquals(listOf(2), board.items.map { it.collected })
        }

    @Test
    fun `REQ-GAME-011 confirmed cooking credits cooking points`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.makeChildCook(family)
            val settings = client.call(HttpMethod.Put, "$GAME/settings", family.parentToken, """{"cookingPoints":4}""")
            assertEquals(HttpStatusCode.OK, settings.status)
            val dishId = client.enterApprovedChildDish(family, COOKED_DAY)

            client.call(HttpMethod.Post, "/api/v1/meals/dishes/$dishId/cooking-confirmation", family.parentToken)

            val account = client.accountOf(family.parentToken, family.childId)
            assertEquals(listOf(LedgerEntryKind.COOKING to 4), account.items.map { it.kind to it.amount })
        }

    @Test
    fun `REQ-GAME-013 parent books, child gets 403 on booking`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            assertEquals(HttpStatusCode.Created, client.book(family.parentToken, family.childId, 12, "Übernahme Küchenboard").status)
            assertEquals(HttpStatusCode.Created, client.book(family.parentToken, family.childId, -2).status)
            assertEquals(HttpStatusCode.Forbidden, client.book(family.childToken, family.childId, 5).status)

            val account = client.accountOf(family.childToken, family.childId)
            assertEquals(10, account.collected)
            assertEquals(10, account.balance)
            val invalid =
                client.call(
                    HttpMethod.Post,
                    "$GAME/members/${family.childId}/entries",
                    family.parentToken,
                    """{"amount":0,"reason":"x"}""",
                    invalidRequestExpected = true,
                )
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
        }

    @Test
    fun `REQ-GAME-012 child sees only own account`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.memberIdOf(family.parentToken)

            assertEquals(
                HttpStatusCode.OK,
                client.call(HttpMethod.Get, "$GAME/members/${family.childId}/account", family.childToken).status,
            )
            assertEquals(
                HttpStatusCode.NotFound,
                client.call(HttpMethod.Get, "$GAME/members/${UUID.randomUUID()}/account", family.childToken).status,
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                client.call(HttpMethod.Get, "$GAME/members/$parentId/account", family.parentToken).status,
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                client
                    .call(
                        HttpMethod.Get,
                        "$GAME/members/${family.childId}/account?limit=0",
                        family.parentToken,
                        invalidRequestExpected = true,
                    ).status,
            )
        }

    @Test
    fun `REQ-GAME-014 everyone sees the leaderboard`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.book(family.parentToken, family.childId, 7)

            listOf(family.parentToken, family.childToken).forEach { token ->
                val response = client.call(HttpMethod.Get, "$GAME/leaderboard", token)
                assertEquals(HttpStatusCode.OK, response.status)
                val board = checkNotNull(json.decodeFromString<LeaderboardResponse>(response.bodyAsText()).data)
                assertEquals(listOf("Lea"), board.items.map { it.name })
                assertEquals(listOf(7), board.items.map { it.collected })
            }
        }

    @Test
    fun `REQ-GAME-011 settings child reads, only parents update, invalid value 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val read = client.call(HttpMethod.Get, "$GAME/settings", family.childToken)
            assertEquals(3, checkNotNull(json.decodeFromString<GameSettingsResponse>(read.bodyAsText()).data).cookingPoints)
            assertEquals(
                HttpStatusCode.Forbidden,
                client.call(HttpMethod.Put, "$GAME/settings", family.childToken, """{"cookingPoints":5}""").status,
            )
            val invalid =
                client.call(
                    HttpMethod.Put,
                    "$GAME/settings",
                    family.parentToken,
                    """{"cookingPoints":21}""",
                    invalidRequestExpected = true,
                )
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
            assertEquals(
                HttpStatusCode.OK,
                client.call(HttpMethod.Put, "$GAME/settings", family.parentToken, """{"cookingPoints":5}""").status,
            )
        }
}
