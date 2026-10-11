package de.familyplanner.app

import de.familyplanner.api.model.HealthStatus
import de.familyplanner.api.model.MeResponse
import de.familyplanner.api.model.PairingResultResponse
import de.familyplanner.api.model.Role
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthApiTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `REQ-OPS-001 health reports ok`() =
        testApplication {
            application { module(testConfig(dir)) }

            val response = client.call(HttpMethod.Get, "/health")

            assertEquals(HttpStatusCode.OK, response.status)
            val health = json.decodeFromString<HealthStatus>(response.bodyAsText())
            assertEquals(HealthStatus.Status.OK, health.status)
            assertEquals("test", health.version)
        }

    @Test
    fun `REQ-AUTH-002 setup then me returns parent`() =
        testApplication {
            application { module(testConfig(dir)) }

            val setup = client.setup()
            val token = checkNotNull(json.decodeFromString<PairingResultResponse>(setup.bodyAsText()).data).token
            val me = client.call(HttpMethod.Get, "/api/v1/me", token)

            assertEquals(HttpStatusCode.Created, setup.status)
            assertEquals(HttpStatusCode.OK, me.status)
            val data = checkNotNull(json.decodeFromString<MeResponse>(me.bodyAsText()).data)
            assertEquals(Role.PARENT, data.member.role)
            assertEquals("Mama", data.member.name)
        }

    @Test
    fun `REQ-AUTH-002 second setup returns conflict`() =
        testApplication {
            application { module(testConfig(dir)) }
            client.setup()

            val second = client.setup(parentName = "Papa")

            assertEquals(HttpStatusCode.Conflict, second.status)
            assertTrue(second.bodyAsText().contains("CONFLICT"))
        }

    @Test
    fun `REQ-AUTH-002 setup with wrong token is unauthorized with message`() =
        testApplication {
            application { module(testConfig(dir)) }

            val response =
                client.call(
                    HttpMethod.Post,
                    "/api/v1/setup",
                    body = """{"setupToken":"wrong-setup-token-1234","parentName":"Mama","deviceName":"Handy"}""",
                )

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().contains("Setup-Token ungültig"))
        }

    @Test
    fun `REQ-AUTH-002 setup is unavailable without configured token`() =
        testApplication {
            application { module(testConfig(dir).copy(setupToken = null)) }

            val response = client.setup()

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        }

    @Test
    fun `REQ-AUTH-002 request without token is unauthorized`() =
        testApplication {
            application { module(testConfig(dir)) }

            val response = client.call(HttpMethod.Get, "/api/v1/me")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().contains("UNAUTHORIZED"))
        }

    @Test
    fun `REQ-AUTH-002 request with invalid token is unauthorized`() =
        testApplication {
            application { module(testConfig(dir)) }

            val response = client.call(HttpMethod.Get, "/api/v1/me", token = "not-a-real-token")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `REQ-AUTH-002 child pairs with code and sees itself in me`() =
        testApplication {
            application { module(testConfig(dir)) }

            val family = setupFamilyWithChild()
            val me = client.call(HttpMethod.Get, "/api/v1/me", family.childToken)

            val data = checkNotNull(json.decodeFromString<MeResponse>(me.bodyAsText()).data)
            assertEquals(family.childId, data.member.id)
            assertEquals(Role.CHILD, data.member.role)
            assertEquals(family.childDeviceId, data.deviceId)
        }

    @Test
    fun `REQ-AUTH-002 pairing with unknown code is unauthorized`() =
        testApplication {
            application { module(testConfig(dir)) }

            val response = client.call(HttpMethod.Post, "/api/v1/devices/pair", body = """{"code":"000000","deviceName":"Handy"}""")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().contains("INVALID_PAIRING_CODE"))
        }

    @Test
    fun `REQ-AUTH-002 pairing code can be redeemed only once`() =
        testApplication {
            application { module(testConfig(dir)) }
            val family = setupFamilyWithChild()
            val code =
                client
                    .call(HttpMethod.Post, "/api/v1/members/${family.childId}/pairing-codes", family.parentToken)
                    .bodyAsText()
                    .substringAfter("\"code\":\"")
                    .substringBefore("\"")

            val first = client.call(HttpMethod.Post, "/api/v1/devices/pair", body = """{"code":"$code","deviceName":"A"}""")
            val second = client.call(HttpMethod.Post, "/api/v1/devices/pair", body = """{"code":"$code","deviceName":"B"}""")

            assertEquals(HttpStatusCode.Created, first.status)
            assertEquals(HttpStatusCode.Unauthorized, second.status)
        }

    @Test
    fun `malformed pairing code returns 400`() =
        testApplication {
            application { module(testConfig(dir)) }

            val response =
                client.call(
                    HttpMethod.Post,
                    "/api/v1/devices/pair",
                    body = """{"code":"12ab","deviceName":"Handy"}""",
                    invalidRequestExpected = true,
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `pairing endpoint is rate limited`() =
        testApplication {
            application { module(testConfig(dir, RateLimits(globalPerMinute = 100_000, pairingPerMinute = 5))) }

            val statuses =
                (1..6).map {
                    client.call(HttpMethod.Post, "/api/v1/devices/pair", body = """{"code":"000000","deviceName":"Handy"}""").status
                }

            assertEquals(List(5) { HttpStatusCode.Unauthorized } + HttpStatusCode.TooManyRequests, statuses)
        }

    @Test
    fun `contract validator detects invalid response`() {
        val valid = contract.report("GET", "/health", 200, "application/json", """{"status":"ok","version":"x"}""")
        val invalid = contract.report("GET", "/health", 200, "application/json", """{"status":"bogus","version":"x"}""")

        assertFalse(valid.hasErrors(), valid.text())
        assertTrue(invalid.hasErrors())
    }
}
