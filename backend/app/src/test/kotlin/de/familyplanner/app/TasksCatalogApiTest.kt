package de.familyplanner.app

import de.familyplanner.api.model.CatalogTask
import de.familyplanner.api.model.CatalogTaskListResponse
import de.familyplanner.api.model.CatalogTaskResponse
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
import kotlin.test.assertTrue

private const val CATALOG = "/api/v1/tasks/catalog"

class TasksCatalogApiTest {
    @TempDir
    lateinit var dir: Path

    private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }

    private suspend fun HttpResponse.task(): CatalogTask = checkNotNull(json.decodeFromString<CatalogTaskResponse>(bodyAsText()).data)

    private suspend fun HttpClient.catalog(
        token: String,
        query: String = "",
    ): List<CatalogTask> =
        checkNotNull(json.decodeFromString<CatalogTaskListResponse>(call(HttpMethod.Get, "$CATALOG$query", token).bodyAsText()).data)

    @Test
    fun `REQ-TASK-010 parent manages the catalog`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val created = client.call(HttpMethod.Post, CATALOG, family.parentToken, """{"title":" Bad putzen ","emoji":"🛁","points":3}""")
            assertEquals(HttpStatusCode.Created, created.status)
            val task = created.task()
            assertEquals(CatalogTask(task.id, "Bad putzen", 3, archived = false, emoji = "🛁"), task)

            val updated = client.call(HttpMethod.Put, "$CATALOG/${task.id}", family.parentToken, """{"title":"Bad schrubben","points":4}""")
            assertEquals(HttpStatusCode.OK, updated.status)
            assertEquals(CatalogTask(task.id, "Bad schrubben", 4, archived = false), updated.task())

            assertEquals(listOf("Bad schrubben"), client.catalog(family.parentToken).map { it.title })
            assertEquals(HttpStatusCode.NoContent, client.call(HttpMethod.Delete, "$CATALOG/${task.id}", family.parentToken).status)
            assertTrue(client.catalog(family.parentToken).isEmpty())
            assertEquals(listOf(true), client.catalog(family.parentToken, "?includeArchived=true").map { it.archived })
        }

    @Test
    fun `REQ-TASK-010 child gets 403 on the catalog`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val id = UUID.randomUUID()
            val body = """{"title":"x","points":1}"""

            assertEquals(HttpStatusCode.Forbidden, client.call(HttpMethod.Get, CATALOG, family.childToken).status)
            assertEquals(HttpStatusCode.Forbidden, client.call(HttpMethod.Post, CATALOG, family.childToken, body).status)
            assertEquals(HttpStatusCode.Forbidden, client.call(HttpMethod.Put, "$CATALOG/$id", family.childToken, body).status)
            assertEquals(HttpStatusCode.Forbidden, client.call(HttpMethod.Delete, "$CATALOG/$id", family.childToken).status)
        }

    @Test
    fun `REQ-TASK-010 unauthenticated request gets 401`() =
        testApplication {
            start()

            assertEquals(HttpStatusCode.Unauthorized, client.call(HttpMethod.Get, CATALOG).status)
        }

    @Test
    fun `REQ-TASK-010 invalid catalog input gets 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            listOf("""{"title":"x","points":0}""", """{"title":"","points":1}""", """{"title":"x","points":101}""").forEach {
                val response = client.call(HttpMethod.Post, CATALOG, family.parentToken, it, invalidRequestExpected = true)
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
            val tooLong = """{"title":"x","emoji":"${"e".repeat(17)}","points":1}"""
            assertEquals(
                HttpStatusCode.BadRequest,
                client.call(HttpMethod.Post, CATALOG, family.parentToken, tooLong, invalidRequestExpected = true).status,
            )
        }

    @Test
    fun `REQ-TASK-010 unknown task gets 404, archived task cannot be edited, bad id gets 400, bad flag gets 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val body = """{"title":"x","points":1}"""
            val archived = client.call(HttpMethod.Post, CATALOG, family.parentToken, body).task()
            client.call(HttpMethod.Delete, "$CATALOG/${archived.id}", family.parentToken)

            assertEquals(
                HttpStatusCode.NotFound,
                client.call(HttpMethod.Put, "$CATALOG/${UUID.randomUUID()}", family.parentToken, body).status,
            )
            assertEquals(
                HttpStatusCode.NotFound,
                client.call(HttpMethod.Delete, "$CATALOG/${UUID.randomUUID()}", family.parentToken).status,
            )
            assertEquals(HttpStatusCode.Conflict, client.call(HttpMethod.Put, "$CATALOG/${archived.id}", family.parentToken, body).status)
            assertEquals(
                HttpStatusCode.BadRequest,
                client.call(HttpMethod.Delete, "$CATALOG/nope", family.parentToken, invalidRequestExpected = true).status,
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                client.call(HttpMethod.Get, "$CATALOG?includeArchived=maybe", family.parentToken, invalidRequestExpected = true).status,
            )
        }
}
