package de.familyplanner.android.feature.auth.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.api.client.AuthApi
import de.familyplanner.api.client.DevicesApi
import de.familyplanner.api.client.MembersApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RemoteRepositoriesTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    private lateinit var auth: RemoteAuthRepository
    private lateinit var family: RemoteFamilyRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        auth = RemoteAuthRepository(retrofit.create(AuthApi::class.java), json)
        family =
            RemoteFamilyRepository(retrofit.create(MembersApi::class.java), retrofit.create(DevicesApi::class.java), json)
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

    private val member = """{"id":"m-1","name":"Lea","role":"CHILD","createdAt":"2026-10-12T10:00:00Z"}"""
    private val device = """{"id":"d-1","memberId":"m-1","name":"Pixel","createdAt":"2026-10-12T10:00:00Z","lastSeenAt":null}"""

    @Test
    fun `pair sends bearer-less request and maps the session`() =
        runTest {
            enqueue(201, """{"success":true,"data":{"token":"tok","member":$member,"device":$device}}""")

            val result = auth.pair("123456", "Pixel")

            assertIs<ApiResult.Success<*>>(result)
            val request = server.takeRequest()
            assertEquals("/api/v1/devices/pair", request.url.encodedPath)
            assertNull(request.headers["Authorization"])
            assertEquals("""{"code":"123456","deviceName":"Pixel"}""", request.body?.utf8())
            assertEquals("tok", (result as ApiResult.Success).data.token)
            assertEquals(Role.CHILD, result.data.role)
        }

    @Test
    fun `me maps member and device id`() =
        runTest {
            enqueue(200, """{"success":true,"data":{"member":$member,"deviceId":"d-1"}}""")

            val result = auth.me()

            assertEquals("d-1", (result as ApiResult.Success).data.deviceId)
        }

    @Test
    fun `devices parse optional lastSeenAt`() =
        runTest {
            val seen =
                """{"id":"d-2","memberId":"m-1","name":"Tab","createdAt":"2026-10-12T10:00:00Z",""" +
                    """"lastSeenAt":"2026-10-12T11:00:00Z"}"""
            enqueue(200, """{"success":true,"data":[$device,$seen]}""")

            val result = family.devices()

            val devices = (result as ApiResult.Success).data
            assertNull(devices[0].lastSeenAt)
            assertEquals(Instant.parse("2026-10-12T11:00:00Z"), devices[1].lastSeenAt)
        }

    @Test
    fun `device with unreadable timestamp becomes server error`() =
        runTest {
            val broken = """{"id":"d-2","memberId":"m-1","name":"Tab","createdAt":"x","lastSeenAt":"gestern"}"""
            enqueue(200, """{"success":true,"data":[$broken]}""")

            assertEquals(ApiResult.Failure(AppError.Server), family.devices())
        }

    @Test
    fun `pairing code maps expiry and unreadable expiry fails`() =
        runTest {
            enqueue(201, """{"success":true,"data":{"code":"483920","expiresAt":"2026-10-12T16:42:00Z"}}""")
            val ok = family.createPairingCode("m-1")
            assertEquals(Instant.parse("2026-10-12T16:42:00Z"), (ok as ApiResult.Success).data.expiresAt)
            assertEquals("/api/v1/members/m-1/pairing-codes", server.takeRequest().url.encodedPath)

            enqueue(201, """{"success":true,"data":{"code":"483920","expiresAt":"bald"}}""")
            assertEquals(ApiResult.Failure(AppError.Server), family.createPairingCode("m-1"))
        }

    @Test
    fun `create member and revoke device hit the contract endpoints`() =
        runTest {
            enqueue(201, """{"success":true,"data":$member}""")
            assertIs<ApiResult.Success<*>>(family.createMember("Lea", Role.CHILD))
            assertEquals("""{"name":"Lea","role":"CHILD"}""", server.takeRequest().body?.utf8())

            enqueue(204)
            assertEquals(ApiResult.Success(Unit), family.revokeDevice("d-1"))
            assertEquals("DELETE", server.takeRequest().method)

            enqueue(200, """{"success":true,"data":[$member]}""")
            assertEquals(Role.CHILD, (family.members() as ApiResult.Success).data.single().role)
        }
}
