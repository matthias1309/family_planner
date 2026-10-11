package de.familyplanner.app

import de.familyplanner.api.model.DeviceListResponse
import de.familyplanner.api.model.MemberListResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberDeviceApiTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `REQ-AUTH-001 parent creates and lists members`() =
        testApplication {
            application { module(testConfig(dir)) }
            val family = setupFamilyWithChild()

            val list = client.call(HttpMethod.Get, "/api/v1/members", family.parentToken)

            assertEquals(HttpStatusCode.OK, list.status)
            val names = checkNotNull(json.decodeFromString<MemberListResponse>(list.bodyAsText()).data).map { it.name }
            assertEquals(listOf("Mama", "Lea"), names)
        }

    @Test
    fun `REQ-AUTH-001 child can list members`() =
        testApplication {
            application { module(testConfig(dir)) }
            val family = setupFamilyWithChild()

            val list = client.call(HttpMethod.Get, "/api/v1/members", family.childToken)

            assertEquals(HttpStatusCode.OK, list.status)
        }

    @Test
    fun `REQ-AUTH-002 child device cannot create members`() =
        testApplication {
            application { module(testConfig(dir)) }
            val family = setupFamilyWithChild()

            val response = client.call(HttpMethod.Post, "/api/v1/members", family.childToken, """{"name":"Gast","role":"CHILD"}""")

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `REQ-AUTH-002 child device cannot use parent endpoints`() =
        testApplication {
            application { module(testConfig(dir)) }
            val family = setupFamilyWithChild()

            val devices = client.call(HttpMethod.Get, "/api/v1/devices", family.childToken)
            val code = client.call(HttpMethod.Post, "/api/v1/members/${family.childId}/pairing-codes", family.childToken)
            val revoke = client.call(HttpMethod.Delete, "/api/v1/devices/${family.childDeviceId}", family.childToken)

            assertEquals(
                listOf(HttpStatusCode.Forbidden, HttpStatusCode.Forbidden, HttpStatusCode.Forbidden),
                listOf(devices.status, code.status, revoke.status),
            )
        }

    @Test
    fun `REQ-AUTH-001 duplicate member name returns conflict`() =
        testApplication {
            application { module(testConfig(dir)) }
            val family = setupFamilyWithChild()

            val response = client.call(HttpMethod.Post, "/api/v1/members", family.parentToken, """{"name":"lea","role":"CHILD"}""")

            assertEquals(HttpStatusCode.Conflict, response.status)
        }

    @Test
    fun `REQ-AUTH-001 invalid member names and roles return 400`() =
        testApplication {
            application { module(testConfig(dir)) }
            val family = setupFamilyWithChild()

            suspend fun create(
                body: String,
                invalid: Boolean = true,
            ) = client.call(HttpMethod.Post, "/api/v1/members", family.parentToken, body, invalidRequestExpected = invalid)
            val blank = create("""{"name":"  ","role":"CHILD"}""")
            val tooLong = create("""{"name":"${"x".repeat(41)}","role":"CHILD"}""")
            val badRole = create("""{"name":"Neu","role":"ADMIN"}""")
            val maxLength = create("""{"name":"${"y".repeat(40)}","role":"CHILD"}""", invalid = false)

            assertEquals(
                listOf(HttpStatusCode.BadRequest, HttpStatusCode.BadRequest, HttpStatusCode.BadRequest, HttpStatusCode.Created),
                listOf(blank.status, tooLong.status, badRole.status, maxLength.status),
            )
        }

    @Test
    fun `unknown json fields are ignored, malformed json returns 400`() =
        testApplication {
            application { module(testConfig(dir)) }
            val family = setupFamilyWithChild()

            val unknownField =
                client.call(
                    HttpMethod.Post,
                    "/api/v1/members",
                    family.parentToken,
                    """{"name":"Tom","role":"CHILD","extra":1}""",
                    validateContract = false,
                )
            val malformed = client.call(HttpMethod.Post, "/api/v1/members", family.parentToken, "{not json", validateContract = false)

            assertEquals(HttpStatusCode.Created, unknownField.status)
            assertEquals(HttpStatusCode.BadRequest, malformed.status)
            assertTrue(malformed.bodyAsText().contains("VALIDATION_FAILED"))
        }

    @Test
    fun `invalid member id in path returns 400`() =
        testApplication {
            application { module(testConfig(dir)) }
            val family = setupFamilyWithChild()

            val response =
                client.call(
                    HttpMethod.Post,
                    "/api/v1/members/not-a-uuid/pairing-codes",
                    family.parentToken,
                    invalidRequestExpected = true,
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `REQ-AUTH-002 pairing code for unknown member returns 404`() =
        testApplication {
            application { module(testConfig(dir)) }
            val family = setupFamilyWithChild()

            val response =
                client.call(
                    HttpMethod.Post,
                    "/api/v1/members/00000000-0000-4000-8000-000000000000/pairing-codes",
                    family.parentToken,
                )

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `REQ-AUTH-003 parent revokes device and its token stops working`() =
        testApplication {
            application { module(testConfig(dir)) }
            val family = setupFamilyWithChild()
            val devices = client.call(HttpMethod.Get, "/api/v1/devices", family.parentToken)
            val before = checkNotNull(json.decodeFromString<DeviceListResponse>(devices.bodyAsText()).data)

            val revoke = client.call(HttpMethod.Delete, "/api/v1/devices/${family.childDeviceId}", family.parentToken)
            val afterRevoke = client.call(HttpMethod.Get, "/api/v1/me", family.childToken)
            val revokeAgain = client.call(HttpMethod.Delete, "/api/v1/devices/${family.childDeviceId}", family.parentToken)

            assertEquals(2, before.size)
            assertEquals(HttpStatusCode.NoContent, revoke.status)
            assertEquals(HttpStatusCode.Unauthorized, afterRevoke.status)
            assertEquals(HttpStatusCode.NotFound, revokeAgain.status)
        }
}
