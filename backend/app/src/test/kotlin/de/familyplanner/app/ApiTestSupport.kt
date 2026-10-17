package de.familyplanner.app

import de.familyplanner.api.model.MemberResponse
import de.familyplanner.api.model.PairingCodeResponse
import de.familyplanner.api.model.PairingResultResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

/** Wednesday 14.10.2026 -> the plan week being planned is the one starting Monday 2026-10-19. */
val FIXED_NOW: Clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC)

const val SETUP_TOKEN = "test-setup-token-123456"

val contract = ContractValidator()
val json = Json { ignoreUnknownKeys = true }

fun testConfig(
    dir: Path,
    rateLimits: RateLimits = RateLimits(globalPerMinute = 100_000, pairingPerMinute = 100_000),
) = AppConfig(dbPath = dir.resolve("test.db"), setupToken = SETUP_TOKEN, version = "test", rateLimits = rateLimits)

data class Family(
    val parentToken: String,
    val childId: String,
    val childToken: String,
    val childDeviceId: String,
)

/** Performs a request and validates request + response against the OpenAPI contract. */
suspend fun HttpClient.call(
    method: HttpMethod,
    path: String,
    token: String? = null,
    body: String? = null,
    validateContract: Boolean = true,
    invalidRequestExpected: Boolean = false,
): HttpResponse {
    val response =
        request(path) {
            this.method = method
            token?.let { bearerAuth(it) }
            body?.let {
                contentType(ContentType.Application.Json)
                setBody(it)
            }
        }
    if (validateContract) contract.assertValid(method.value, path, response, body, invalidRequestExpected)
    return response
}

suspend fun HttpClient.setup(
    parentName: String = "Mama",
    deviceName: String = "Handy Mama",
): HttpResponse =
    call(
        HttpMethod.Post,
        "/api/v1/setup",
        body = """{"setupToken":"$SETUP_TOKEN","parentName":"$parentName","deviceName":"$deviceName"}""",
    )

suspend fun ApplicationTestBuilder.setupFamilyWithChild(): Family {
    val setupResponse = client.setup()
    assertEquals(HttpStatusCode.Created, setupResponse.status)
    val parent = json.decodeFromString<PairingResultResponse>(setupResponse.bodyAsText())
    val parentToken = checkNotNull(parent.data).token
    val child =
        json.decodeFromString<MemberResponse>(
            client.call(HttpMethod.Post, "/api/v1/members", parentToken, """{"name":"Lea","role":"CHILD"}""").bodyAsText(),
        )
    val childId = checkNotNull(child.data).id
    val code =
        json.decodeFromString<PairingCodeResponse>(
            client.call(HttpMethod.Post, "/api/v1/members/$childId/pairing-codes", parentToken).bodyAsText(),
        )
    val paired =
        json.decodeFromString<PairingResultResponse>(
            client
                .call(
                    HttpMethod.Post,
                    "/api/v1/devices/pair",
                    body = """{"code":"${checkNotNull(code.data).code}","deviceName":"Handy Lea"}""",
                ).bodyAsText(),
        )
    val pairedData = checkNotNull(paired.data)
    return Family(parentToken, childId, pairedData.token, pairedData.device.id)
}
