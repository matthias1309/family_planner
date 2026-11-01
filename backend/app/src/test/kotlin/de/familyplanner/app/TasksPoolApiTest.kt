package de.familyplanner.app

import de.familyplanner.api.model.CatalogTaskResponse
import de.familyplanner.api.model.TaskBoardResponse
import de.familyplanner.api.model.TaskCard
import de.familyplanner.api.model.TaskCardListResponse
import de.familyplanner.api.model.TaskCardResponse
import de.familyplanner.api.model.TaskStatus
import io.ktor.client.HttpClient
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
import kotlin.test.assertTrue

private const val TASKS = "/api/v1/tasks"

class TasksPoolApiTest {
    @TempDir
    lateinit var dir: Path

    private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }

    private suspend fun HttpClient.catalogTask(
        family: Family,
        selectable: Boolean,
    ): String {
        val body = """{"title":"Bad putzen","points":3,"childSelectable":$selectable}"""
        val response = call(HttpMethod.Post, "$TASKS/catalog", family.parentToken, body)
        return checkNotNull(json.decodeFromString<CatalogTaskResponse>(response.bodyAsText()).data).id
    }

    private suspend fun HttpClient.take(
        token: String,
        catalogId: String,
        alreadyDone: Boolean,
    ) = call(HttpMethod.Post, "$TASKS/pool/$catalogId/cards", token, """{"alreadyDone":$alreadyDone}""")

    private suspend fun HttpClient.childCards(family: Family): List<TaskCard> =
        checkNotNull(json.decodeFromString<TaskBoardResponse>(call(HttpMethod.Get, "$TASKS/board", family.parentToken).bodyAsText()).data)
            .lanes
            .single()
            .cards

    @Test
    fun `REQ-TASK-028 child takes a pool task and reports it at once`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val catalogId = client.catalogTask(family, selectable = true)

            val todo = client.take(family.childToken, catalogId, alreadyDone = false)
            assertEquals(HttpStatusCode.Created, todo.status)
            val todoCard = checkNotNull(json.decodeFromString<TaskCardResponse>(todo.bodyAsText()).data)
            assertEquals(TaskStatus.TODO, todoCard.status)
            assertEquals(true, todoCard.selfAssigned)
            assertEquals(family.childId, todoCard.memberId)
            assertNull(todoCard.dueAt)

            val done = client.take(family.childToken, catalogId, alreadyDone = true)
            assertEquals(HttpStatusCode.Created, done.status)
            val doneCard = checkNotNull(json.decodeFromString<TaskCardResponse>(done.bodyAsText()).data)
            assertEquals(TaskStatus.REVIEW, doneCard.status)
            assertEquals(false, doneCard.reportedLate)

            assertEquals(setOf(todoCard.id, doneCard.id), client.childCards(family).map { it.id }.toSet())
        }

    @Test
    fun `REQ-TASK-028 parent cannot take, unreleased gets 403, archived gets 409, unknown gets 404`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val released = client.catalogTask(family, selectable = true)
            val internal = client.catalogTask(family, selectable = false)
            val archived = client.catalogTask(family, selectable = true)
            client.call(HttpMethod.Delete, "$TASKS/catalog/$archived", family.parentToken)

            assertEquals(HttpStatusCode.Forbidden, client.take(family.parentToken, released, alreadyDone = false).status)
            assertEquals(HttpStatusCode.Forbidden, client.take(family.childToken, internal, alreadyDone = false).status)
            assertEquals(HttpStatusCode.Conflict, client.take(family.childToken, archived, alreadyDone = false).status)
            assertEquals(HttpStatusCode.NotFound, client.take(family.childToken, UUID.randomUUID().toString(), alreadyDone = false).status)
            val missingFlag =
                client.call(
                    HttpMethod.Post,
                    "$TASKS/pool/$released/cards",
                    family.childToken,
                    "{}",
                    invalidRequestExpected = true,
                )
            assertEquals(HttpStatusCode.BadRequest, missingFlag.status)
            assertTrue(client.childCards(family).isEmpty())
        }

    @Test
    fun `REQ-TASK-028 child puts back own taken card but not an assigned one`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val catalogId = client.catalogTask(family, selectable = true)
            val taken =
                checkNotNull(json.decodeFromString<TaskCardResponse>(client.take(family.childToken, catalogId, false).bodyAsText()).data)
            val assignBody = """{"memberId":"${family.childId}","catalogTaskIds":["$catalogId"]}"""
            val assigned =
                checkNotNull(
                    json
                        .decodeFromString<TaskCardListResponse>(
                            client.call(HttpMethod.Post, "$TASKS/cards", family.parentToken, assignBody).bodyAsText(),
                        ).data,
                ).single()
            val reported =
                checkNotNull(json.decodeFromString<TaskCardResponse>(client.take(family.childToken, catalogId, true).bodyAsText()).data)

            fun path(card: TaskCard) = "$TASKS/cards/${card.id}"

            assertEquals(HttpStatusCode.Forbidden, client.call(HttpMethod.Delete, path(assigned), family.childToken).status)
            assertEquals(HttpStatusCode.Conflict, client.call(HttpMethod.Delete, path(reported), family.childToken).status)
            assertEquals(HttpStatusCode.NoContent, client.call(HttpMethod.Delete, path(taken), family.childToken).status)
            assertEquals(HttpStatusCode.NotFound, client.call(HttpMethod.Delete, path(taken), family.childToken).status)
            assertEquals(setOf(assigned.id, reported.id), client.childCards(family).map { it.id }.toSet())
        }
}
