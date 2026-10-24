package de.familyplanner.android.feature.tasks.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogInput
import de.familyplanner.api.client.TasksApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class RemoteCatalogRepositoryTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteCatalogRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        repository = RemoteCatalogRepository(retrofit.create(TasksApi::class.java), json)
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

    private val taskJson = """{"id":"k-1","title":"Bad putzen","emoji":"🛁","points":3,"archived":false}"""

    @Test
    fun `REQ-TASK-010 list maps active tasks and skips archived ones`() =
        runTest {
            enqueue(200, ok("[$taskJson,${taskJson.replace("k-1", "k-2").replace("false", "true")}]"))

            val tasks = (repository.list() as ApiResult.Success).data

            assertEquals("/api/v1/tasks/catalog", server.takeRequest().url.encodedPath)
            assertEquals(listOf("k-1"), tasks.map { it.id })
            assertEquals("🛁", tasks.single().emoji)
        }

    @Test
    fun `REQ-TASK-010 create and update send the input`() =
        runTest {
            enqueue(201, ok(taskJson))
            enqueue(200, ok(taskJson))

            repository.create(CatalogInput("Bad putzen", "🛁", 3))
            repository.update("k-1", CatalogInput("Bad putzen", null, 4))

            val create = server.takeRequest()
            val update = server.takeRequest()
            assertEquals("POST /api/v1/tasks/catalog", "${create.method} ${create.url.encodedPath}")
            assertEquals(
                true,
                create.body
                    ?.utf8()
                    .orEmpty()
                    .contains(""""points":3"""),
            )
            assertEquals("PUT /api/v1/tasks/catalog/k-1", "${update.method} ${update.url.encodedPath}")
            assertEquals(
                false,
                update.body
                    ?.utf8()
                    .orEmpty()
                    .contains("emoji"),
            )
        }

    @Test
    fun `REQ-TASK-010 archive accepts 204 and maps validation errors`() =
        runTest {
            enqueue(204)
            enqueue(400, """{"success":false,"error":{"code":"VALIDATION_FAILED","message":"Symbol ist zu lang"}}""")

            assertEquals(ApiResult.Success(Unit), repository.archive("k-1"))
            assertEquals("DELETE", server.takeRequest().method)
            assertEquals(ApiResult.Failure(AppError.Validation("Symbol ist zu lang")), repository.create(CatalogInput("A", "x", 1)))
        }
}
