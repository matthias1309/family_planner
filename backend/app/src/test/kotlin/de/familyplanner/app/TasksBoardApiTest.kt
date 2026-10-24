package de.familyplanner.app

import de.familyplanner.api.model.CatalogTaskResponse
import de.familyplanner.api.model.TaskBoard
import de.familyplanner.api.model.TaskBoardResponse
import de.familyplanner.api.model.TaskCard
import de.familyplanner.api.model.TaskCardListResponse
import de.familyplanner.api.model.TaskCardResponse
import de.familyplanner.api.model.TaskStatus
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
import kotlin.test.assertNull

private const val TASKS = "/api/v1/tasks"

class TasksBoardApiTest {
    @TempDir
    lateinit var dir: Path

    private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }

    private suspend fun HttpClient.catalogTask(
        family: Family,
        title: String = "Bad putzen",
        points: Int = 3,
    ): String =
        checkNotNull(
            json
                .decodeFromString<CatalogTaskResponse>(
                    call(HttpMethod.Post, "$TASKS/catalog", family.parentToken, """{"title":"$title","points":$points}""").bodyAsText(),
                ).data,
        ).id

    private suspend fun HttpClient.assign(
        family: Family,
        vararg catalogIds: String,
    ): List<TaskCard> {
        val body = """{"memberId":"${family.childId}","catalogTaskIds":[${catalogIds.joinToString { "\"$it\"" }}]}"""
        val response = call(HttpMethod.Post, "$TASKS/cards", family.parentToken, body)
        assertEquals(HttpStatusCode.Created, response.status)
        return checkNotNull(json.decodeFromString<TaskCardListResponse>(response.bodyAsText()).data)
    }

    private suspend fun HttpClient.board(token: String): TaskBoard =
        checkNotNull(json.decodeFromString<TaskBoardResponse>(call(HttpMethod.Get, "$TASKS/board", token).bodyAsText()).data)

    private suspend fun HttpResponse.card(): TaskCard = checkNotNull(json.decodeFromString<TaskCardResponse>(bodyAsText()).data)

    private suspend fun HttpClient.act(
        token: String,
        card: TaskCard,
        action: String,
        body: String? = null,
    ) = call(HttpMethod.Post, "$TASKS/cards/${card.id}/$action", token, body)

    private suspend fun ApplicationTestBuilder.familyWithCard(): Pair<Family, TaskCard> {
        val family = setupFamilyWithChild()
        return family to client.assign(family, client.catalogTask(family)).single()
    }

    @Test
    fun `REQ-TASK-020 parent assigns and everyone sees the board`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val first = client.catalogTask(family, "Spülmaschine ausräumen", 1)
            val second = client.catalogTask(family, "Bad putzen", 3)

            val cards = client.assign(family, first, second)

            assertEquals(listOf(TaskStatus.TODO, TaskStatus.TODO), cards.map { it.status })
            assertEquals(listOf(1, 3), cards.map { it.points })
            listOf(family.parentToken, family.childToken).forEach { token ->
                val lane = client.board(token).lanes.single()
                assertEquals("Lea", lane.name)
                assertEquals(cards.map { it.id }, lane.cards.map { it.id })
            }
        }

    @Test
    fun `REQ-TASK-020 assigning needs a parent, a child target and available tasks`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val task = client.catalogTask(family)
            val parentId = client.memberIdOf(family.parentToken)

            fun body(
                member: String,
                ids: String,
            ) = """{"memberId":"$member","catalogTaskIds":[$ids]}"""

            val childCall = client.call(HttpMethod.Post, "$TASKS/cards", family.childToken, body(family.childId, "\"$task\""))
            assertEquals(HttpStatusCode.Forbidden, childCall.status)
            val toParent = client.call(HttpMethod.Post, "$TASKS/cards", family.parentToken, body(parentId, "\"$task\""))
            assertEquals(HttpStatusCode.BadRequest, toParent.status)
            val unknownTask =
                client.call(
                    HttpMethod.Post,
                    "$TASKS/cards",
                    family.parentToken,
                    body(family.childId, "\"${UUID.randomUUID()}\""),
                )
            assertEquals(HttpStatusCode.BadRequest, unknownTask.status)
            val unknownMember =
                client.call(
                    HttpMethod.Post,
                    "$TASKS/cards",
                    family.parentToken,
                    body(UUID.randomUUID().toString(), "\"$task\""),
                )
            assertEquals(HttpStatusCode.NotFound, unknownMember.status)
            val empty =
                client.call(
                    HttpMethod.Post,
                    "$TASKS/cards",
                    family.parentToken,
                    body(family.childId, ""),
                    invalidRequestExpected = true,
                )
            assertEquals(HttpStatusCode.BadRequest, empty.status)
        }

    @Test
    fun `REQ-TASK-022 child reports and withdraws`() =
        testApplication {
            start()
            val (family, card) = familyWithCard()

            val reported = client.act(family.childToken, card, "report")
            assertEquals(HttpStatusCode.OK, reported.status)
            assertEquals(TaskStatus.REVIEW, reported.card().status)

            val withdrawn = client.act(family.childToken, card, "withdrawal")
            assertEquals(TaskStatus.TODO, withdrawn.card().status)
            assertNull(withdrawn.card().reportedAt)
        }

    @Test
    fun `REQ-TASK-023 parent accepts and rejects`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val (first, second) = client.assign(family, client.catalogTask(family), client.catalogTask(family, "Müll raus", 1))
            client.act(family.childToken, first, "report")
            client.act(family.childToken, second, "report")

            val accepted = client.act(family.parentToken, first, "acceptance")
            assertEquals(HttpStatusCode.OK, accepted.status)
            assertEquals(TaskStatus.DONE, accepted.card().status)

            val rejected = client.act(family.parentToken, second, "rejection", """{"reason":"nicht sauber"}""")
            assertEquals(TaskStatus.TODO, rejected.card().status)
            assertEquals("nicht sauber", rejected.card().rejectionReason)
        }

    @Test
    fun `REQ-TASK-023 rejection works without a body`() =
        testApplication {
            start()
            val (family, card) = familyWithCard()
            client.act(family.childToken, card, "report")

            val rejected = client.act(family.parentToken, card, "rejection")

            assertEquals(HttpStatusCode.OK, rejected.status)
            assertNull(rejected.card().rejectionReason)
        }

    @Test
    fun `REQ-TASK-023 second acceptance is a conflict`() =
        testApplication {
            start()
            val (family, card) = familyWithCard()

            assertEquals(HttpStatusCode.OK, client.act(family.parentToken, card, "acceptance").status)
            assertEquals(HttpStatusCode.Conflict, client.act(family.parentToken, card, "acceptance").status)
            assertEquals(HttpStatusCode.Conflict, client.act(family.childToken, card, "report").status)
        }

    @Test
    fun `REQ-TASK-022 child cannot accept and cannot report for a sibling`() =
        testApplication {
            start()
            val (family, card) = familyWithCard()
            val sibling = pairSibling(family)

            assertEquals(HttpStatusCode.Forbidden, client.act(family.childToken, card, "acceptance").status)
            assertEquals(HttpStatusCode.Forbidden, client.act(family.childToken, card, "rejection").status)
            assertEquals(HttpStatusCode.Forbidden, client.act(sibling, card, "report").status)
            assertEquals(HttpStatusCode.Forbidden, client.act(family.parentToken, card, "report").status)
        }

    @Test
    fun `REQ-TASK-024 parent removes a card, done card cannot be removed`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val (open, done) = client.assign(family, client.catalogTask(family), client.catalogTask(family, "Müll raus", 1))
            client.act(family.parentToken, done, "acceptance")

            assertEquals(HttpStatusCode.Forbidden, client.call(HttpMethod.Delete, "$TASKS/cards/${open.id}", family.childToken).status)
            assertEquals(HttpStatusCode.NoContent, client.call(HttpMethod.Delete, "$TASKS/cards/${open.id}", family.parentToken).status)
            assertEquals(HttpStatusCode.Conflict, client.call(HttpMethod.Delete, "$TASKS/cards/${done.id}", family.parentToken).status)
            assertEquals(HttpStatusCode.NotFound, client.call(HttpMethod.Delete, "$TASKS/cards/${open.id}", family.parentToken).status)
        }

    @Test
    fun `REQ-TASK-025 history returns done cards of a child`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val cards = client.assign(family, client.catalogTask(family), client.catalogTask(family, "Müll raus", 1))
            client.act(family.parentToken, cards[0], "acceptance")

            val path = "$TASKS/members/${family.childId}/history"
            val history =
                checkNotNull(
                    json.decodeFromString<TaskCardListResponse>(client.call(HttpMethod.Get, path, family.childToken).bodyAsText()).data,
                )

            assertEquals(listOf(cards[0].id), history.map { it.id })
            assertEquals(HttpStatusCode.OK, client.call(HttpMethod.Get, "$path?limit=1", family.parentToken).status)
            assertEquals(
                HttpStatusCode.BadRequest,
                client.call(HttpMethod.Get, "$path?limit=0", family.parentToken, invalidRequestExpected = true).status,
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                client.call(HttpMethod.Get, "$path?limit=abc", family.parentToken, invalidRequestExpected = true).status,
            )
            assertEquals(
                HttpStatusCode.NotFound,
                client.call(HttpMethod.Get, "$TASKS/members/${UUID.randomUUID()}/history", family.parentToken).status,
            )
        }

    @Test
    fun `REQ-TASK-022 unknown card gets 404, malformed id gets 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            assertEquals(
                HttpStatusCode.NotFound,
                client.call(HttpMethod.Post, "$TASKS/cards/${UUID.randomUUID()}/report", family.childToken).status,
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                client.call(HttpMethod.Post, "$TASKS/cards/nope/acceptance", family.parentToken, invalidRequestExpected = true).status,
            )
        }

    /** A second child with its own device. */
    private suspend fun ApplicationTestBuilder.pairSibling(family: Family): String {
        val member =
            client.call(HttpMethod.Post, "/api/v1/members", family.parentToken, """{"name":"Ben","role":"CHILD"}""")
        val memberId = checkNotNull(json.decodeFromString<de.familyplanner.api.model.MemberResponse>(member.bodyAsText()).data).id
        val code = client.call(HttpMethod.Post, "/api/v1/members/$memberId/pairing-codes", family.parentToken)
        val codeValue = checkNotNull(json.decodeFromString<de.familyplanner.api.model.PairingCodeResponse>(code.bodyAsText()).data).code
        val paired = client.call(HttpMethod.Post, "/api/v1/devices/pair", body = """{"code":"$codeValue","deviceName":"Handy Ben"}""")
        return checkNotNull(json.decodeFromString<de.familyplanner.api.model.PairingResultResponse>(paired.bodyAsText()).data).token
    }
}
