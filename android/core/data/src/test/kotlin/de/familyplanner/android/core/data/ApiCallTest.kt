package de.familyplanner.android.core.data

import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.api.client.AuthApi
import de.familyplanner.api.client.DevicesApi
import de.familyplanner.api.model.PairDeviceRequest
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class ApiCallTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    private lateinit var auth: AuthApi
    private lateinit var devices: DevicesApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        auth = retrofit.create(AuthApi::class.java)
        devices = retrofit.create(DevicesApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun pair(): ApiResult<String> =
        apiCall(json, { auth.pairDevice(PairDeviceRequest("123456", "Pixel")) }) { it.data?.token }

    private fun enqueue(
        code: Int,
        body: String,
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .addHeader("Content-Type", "application/json")
            .build(),
    )

    private fun error(code: String) = """{"success":false,"error":{"code":"$code","message":"Meldung"}}"""

    private val pairingOk =
        """{"success":true,"data":{"token":"tok","member":{"id":"m","name":"Lea","role":"CHILD","createdAt":"2026-10-12T10:00:00Z"},
            |"device":{"id":"d","memberId":"m","name":"Pixel","createdAt":"2026-10-12T10:00:00Z"}}}
        """.trimMargin()

    @Test
    fun `success envelope with data returns Success`() =
        runTest {
            enqueue(201, pairingOk)
            assertEquals(ApiResult.Success("tok"), pair())
        }

    @Test
    fun `success envelope without data returns Server error`() =
        runTest {
            enqueue(200, """{"success":true}""")
            assertEquals(ApiResult.Failure(AppError.Server), pair())
        }

    @Test
    fun `REQ-AUTH-002 invalid pairing code maps to InvalidPairingCode`() =
        runTest {
            enqueue(401, error("INVALID_PAIRING_CODE"))
            assertEquals(ApiResult.Failure(AppError.InvalidPairingCode), pair())
        }

    @Test
    fun `error codes map to their AppError`() =
        runTest {
            val expected =
                mapOf(
                    400 to ("VALIDATION_FAILED" to AppError.Validation("Meldung")),
                    401 to ("UNAUTHORIZED" to AppError.Unauthorized),
                    403 to ("FORBIDDEN" to AppError.Forbidden),
                    404 to ("NOT_FOUND" to AppError.NotFound),
                    409 to ("CONFLICT" to AppError.Conflict("Meldung")),
                    429 to ("RATE_LIMITED" to AppError.RateLimited),
                    503 to ("SETUP_DISABLED" to AppError.SetupDisabled),
                    500 to ("INTERNAL" to AppError.Server),
                )
            expected.forEach { (status, codeAndError) ->
                enqueue(status, error(codeAndError.first))
                assertEquals(ApiResult.Failure(codeAndError.second), pair(), "status $status")
            }
        }

    @Test
    fun `unreadable error body falls back to http status`() =
        runTest {
            enqueue(500, "<html>boom</html>")
            enqueue(401, "")
            assertEquals(ApiResult.Failure(AppError.Server), pair())
            assertEquals(ApiResult.Failure(AppError.Unauthorized), pair())
        }

    @Test
    fun `dropped connection maps to Network`() =
        runTest {
            server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
            assertEquals(ApiResult.Failure(AppError.Network), pair())
        }

    @Test
    fun `malformed success body maps to Server`() =
        runTest {
            enqueue(200, "not json")
            assertEquals(ApiResult.Failure(AppError.Server), pair())
        }

    @Test
    fun `no content returns Success Unit`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(204).build())
            assertEquals(ApiResult.Success(Unit), apiCallNoContent(json) { devices.revokeDevice("d") })
        }

    @Test
    fun `no content call maps error`() =
        runTest {
            enqueue(403, error("FORBIDDEN"))
            assertEquals(ApiResult.Failure(AppError.Forbidden), apiCallNoContent(json) { devices.revokeDevice("d") })
        }

    @Test
    fun `map transforms success and keeps failure`() {
        assertEquals(ApiResult.Success(2), ApiResult.Success(1).map { it + 1 })
        assertEquals(ApiResult.Failure(AppError.Network), ApiResult.Failure(AppError.Network).map { 1 })
    }
}
