package de.familyplanner.app

import de.familyplanner.api.model.AttendanceExceptionResponse
import de.familyplanner.api.model.AttendanceProfileListResponse
import de.familyplanner.api.model.DeadlineSettingsOverviewResponse
import de.familyplanner.api.model.MeResponse
import de.familyplanner.api.model.MemberWeekAttendance
import de.familyplanner.api.model.WeekAttendanceResponse
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PROFILES = "/api/v1/meals/profiles"
private const val SETTINGS = "/api/v1/meals/settings"
private const val ALTERNATING_PROFILE =
    """{"cooks":true,"pattern":{"type":"ALTERNATING","weekA":["TUESDAY","WEDNESDAY","THURSDAY"],""" +
        """"weekB":["TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],"referenceWeek":"2026-10-19"}}"""

class MealsAttendanceApiTest {
    @TempDir
    lateinit var dir: Path

    private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }

    private suspend fun HttpClient.putAlternatingProfile(
        token: String,
        memberId: String,
    ): HttpResponse = call(HttpMethod.Put, "$PROFILES/$memberId", token, ALTERNATING_PROFILE)

    private suspend fun HttpClient.week(
        token: String,
        weekStart: String,
    ): MemberWeekAttendance {
        val response = call(HttpMethod.Get, "/api/v1/meals/weeks/$weekStart/attendance", token)
        assertEquals(HttpStatusCode.OK, response.status)
        return checkNotNull(json.decodeFromString<WeekAttendanceResponse>(response.bodyAsText()).data).members.single()
    }

    private suspend fun HttpClient.weekResponse(
        token: String,
        weekStart: String,
    ) = checkNotNull(
        json
            .decodeFromString<WeekAttendanceResponse>(call(HttpMethod.Get, "/api/v1/meals/weeks/$weekStart/attendance", token).bodyAsText())
            .data,
    )

    private fun MemberWeekAttendance.presentDays() = days.filter { it.present }.map { it.date.substring(DAY_OF_MONTH_START) }

    private suspend fun HttpClient.createException(
        token: String,
        memberId: String,
        body: String,
        invalid: Boolean = false,
    ) = call(HttpMethod.Post, "$PROFILES/$memberId/exceptions", token, body, invalidRequestExpected = invalid)

    private suspend fun HttpClient.parentId(token: String) =
        checkNotNull(json.decodeFromString<MeResponse>(call(HttpMethod.Get, "/api/v1/me", token).bodyAsText()).data).member.id

    @Test
    fun `REQ-MEAL-010 parent sets alternating pattern and week attendance follows it`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val saved = client.putAlternatingProfile(family.parentToken, family.childId)
            val weekA = client.week(family.childToken, "2026-10-19")
            val weekB = client.week(family.childToken, "2026-10-26")

            assertEquals(HttpStatusCode.OK, saved.status)
            assertEquals(listOf("20", "21", "22"), weekA.presentDays())
            assertEquals(listOf("27", "28", "29", "30", "31"), weekB.presentDays().take(5))
            assertEquals(6, weekB.presentDays().size)
        }

    @Test
    fun `REQ-MEAL-010 child without profile is present every day`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val member = client.week(family.parentToken, "2026-10-19")
            val profiles = client.call(HttpMethod.Get, PROFILES, family.parentToken)

            assertEquals(7, member.presentDays().size)
            assertEquals(false, member.cooks)
            assertNull(member.submissionDeadline)
            val listed = checkNotNull(json.decodeFromString<AttendanceProfileListResponse>(profiles.bodyAsText()).data)
            assertEquals(listOf(family.childId), listed.map { it.memberId })
        }

    @Test
    fun `REQ-MEAL-011 exception overrides pattern and removal restores it`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.putAlternatingProfile(family.parentToken, family.childId)

            val created =
                client.createException(
                    family.parentToken,
                    family.childId,
                    """{"from":"2026-10-19","to":"2026-10-20","presence":"ABSENT","note":"Ferien"}""",
                )
            val during = client.week(family.childToken, "2026-10-19")
            val exceptionId = checkNotNull(json.decodeFromString<AttendanceExceptionResponse>(created.bodyAsText()).data).id
            val deleted = client.call(HttpMethod.Delete, "/api/v1/meals/exceptions/$exceptionId", family.parentToken)
            val after = client.week(family.childToken, "2026-10-19")

            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(listOf("21", "22"), during.presentDays())
            assertEquals("EXCEPTION", during.days[1].source.value)
            assertEquals(HttpStatusCode.NoContent, deleted.status)
            assertEquals(listOf("20", "21", "22"), after.presentDays())
            assertEquals("PATTERN", after.days[1].source.value)
        }

    @Test
    fun `REQ-MEAL-011 present exception turns an absent day into a present one`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.putAlternatingProfile(family.parentToken, family.childId)

            client.createException(family.parentToken, family.childId, """{"from":"2026-10-19","to":"2026-10-19","presence":"PRESENT"}""")

            assertEquals(listOf("19", "20", "21", "22"), client.week(family.childToken, "2026-10-19").presentDays())
        }

    @Test
    fun `REQ-MEAL-011 overlapping exception returns 409 and list is sorted`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val body = """{"from":"2026-12-01","to":"2026-12-05","presence":"ABSENT"}"""
            client.createException(family.parentToken, family.childId, """{"from":"2026-12-20","to":"2026-12-24","presence":"PRESENT"}""")
            client.createException(family.parentToken, family.childId, body)

            val overlap = client.createException(family.parentToken, family.childId, body)
            val list = client.call(HttpMethod.Get, "$PROFILES/${family.childId}/exceptions", family.childToken)

            assertEquals(HttpStatusCode.Conflict, overlap.status)
            assertTrue(list.bodyAsText().indexOf("2026-12-01") < list.bodyAsText().indexOf("2026-12-20"))
        }

    @Test
    fun `REQ-MEAL-012 submission deadline is Thursday 16-00 when child leaves Thursday`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.putAlternatingProfile(family.parentToken, family.childId)

            val response = client.weekResponse(family.childToken, "2026-10-26")

            // running week 19.-25.10. is type A (until Thursday); summer time until 25.10. 03:00
            assertEquals("2026-10-22T14:00:00Z", response.members.single().submissionDeadline)
            assertEquals("2026-10-25T15:00:00Z", response.orderDeadline)
        }

    @Test
    fun `REQ-MEAL-012 submission deadline equals order deadline when present until Sunday`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.putAlternatingProfile(family.parentToken, family.childId)

            val response = client.weekResponse(family.childToken, "2026-11-02")

            // running week 26.10.-01.11. is type B (until Sunday); winter time since 25.10.
            assertEquals("2026-11-01T15:00:00Z", response.members.single().submissionDeadline)
            assertEquals(response.orderDeadline, response.members.single().submissionDeadline)
        }

    @Test
    fun `REQ-MEAL-013 changed order deadline applies from next plan week`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            client.putAlternatingProfile(family.parentToken, family.childId)

            val updated =
                client.call(
                    HttpMethod.Put,
                    SETTINGS,
                    family.parentToken,
                    """{"orderDeadlineDay":"FRIDAY","orderDeadlineTime":"18:00","childDeadlineTime":"16:00"}""",
                )
            val overview = checkNotNull(json.decodeFromString<DeadlineSettingsOverviewResponse>(updated.bodyAsText()).data)
            val read = client.call(HttpMethod.Get, SETTINGS, family.childToken)

            assertEquals(HttpStatusCode.OK, updated.status)
            assertEquals(HttpStatusCode.OK, read.status)
            assertEquals("SUNDAY", overview.current.orderDeadlineDay.value)
            assertEquals("2026-10-26", overview.scheduled?.effectiveFrom)
            assertEquals("2026-10-23T16:00:00Z", client.weekResponse(family.childToken, "2026-10-26").orderDeadline)
            assertEquals("2026-10-18T14:00:00Z", client.weekResponse(family.childToken, "2026-10-19").orderDeadline)
        }

    @Test
    fun `REQ-AUTH-002 child cannot change profiles, exceptions or settings`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val profile = client.putAlternatingProfile(family.childToken, family.childId)
            val exception =
                client.createException(family.childToken, family.childId, """{"from":"2026-12-01","to":"2026-12-02","presence":"ABSENT"}""")
            val settings =
                client.call(
                    HttpMethod.Put,
                    SETTINGS,
                    family.childToken,
                    """{"orderDeadlineDay":"FRIDAY","orderDeadlineTime":"18:00","childDeadlineTime":"16:00"}""",
                )
            val delete = client.call(HttpMethod.Delete, "/api/v1/meals/exceptions/${family.childId}", family.childToken)

            assertEquals(
                List(4) { HttpStatusCode.Forbidden },
                listOf(profile.status, exception.status, settings.status, delete.status),
            )
        }

    @Test
    fun `REQ-AUTH-002 child can read week attendance and profiles`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val statuses =
                listOf(
                    client.call(HttpMethod.Get, PROFILES, family.childToken).status,
                    client.call(HttpMethod.Get, "$PROFILES/${family.childId}/exceptions", family.childToken).status,
                    client.call(HttpMethod.Get, SETTINGS, family.childToken).status,
                    client.call(HttpMethod.Get, "/api/v1/meals/weeks/2026-10-19/attendance", family.childToken).status,
                )

            assertEquals(List(4) { HttpStatusCode.OK }, statuses)
        }

    @Test
    fun `REQ-AUTH-002 meals endpoints require a token`() =
        testApplication {
            start()
            setupFamilyWithChild()

            val response = client.call(HttpMethod.Get, PROFILES)

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `invalid week start returns 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val tuesday = client.call(HttpMethod.Get, "/api/v1/meals/weeks/2026-10-20/attendance", family.parentToken)
            val garbage =
                client.call(HttpMethod.Get, "/api/v1/meals/weeks/soon/attendance", family.parentToken, invalidRequestExpected = true)

            assertEquals(listOf(HttpStatusCode.BadRequest, HttpStatusCode.BadRequest), listOf(tuesday.status, garbage.status))
        }

    @Test
    fun `dates outside the supported year range return 400 instead of 500`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val week =
                client.call(
                    HttpMethod.Get,
                    "/api/v1/meals/weeks/+999999999-12-27/attendance",
                    family.parentToken,
                    invalidRequestExpected = true,
                )
            val exception =
                client.createException(
                    family.parentToken,
                    family.childId,
                    """{"from":"+999999999-01-01","to":"+999999999-01-02","presence":"ABSENT"}""",
                    invalid = true,
                )
            val oldWeek = client.call(HttpMethod.Get, "/api/v1/meals/weeks/1999-12-27/attendance", family.parentToken)

            assertEquals(
                List(3) { HttpStatusCode.BadRequest },
                listOf(week.status, exception.status, oldWeek.status),
            )
        }

    @Test
    fun `REQ-MEAL-013 deadline time with seconds or without leading zero returns 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            suspend fun put(time: String) =
                client.call(
                    HttpMethod.Put,
                    SETTINGS,
                    family.parentToken,
                    """{"orderDeadlineDay":"FRIDAY","orderDeadlineTime":"$time","childDeadlineTime":"16:00"}""",
                    invalidRequestExpected = true,
                )

            assertEquals(listOf(HttpStatusCode.BadRequest, HttpStatusCode.BadRequest), listOf(put("16:00:30").status, put("7:00").status))
        }

    @Test
    fun `REQ-MEAL-013 invalid deadline time returns 400`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            val response =
                client.call(
                    HttpMethod.Put,
                    SETTINGS,
                    family.parentToken,
                    """{"orderDeadlineDay":"FRIDAY","orderDeadlineTime":"25:00","childDeadlineTime":"16:00"}""",
                    invalidRequestExpected = true,
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `REQ-MEAL-010 profile validation returns 400 and 404`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()
            val parentId = client.parentId(family.parentToken)
            val noReference = """{"cooks":true,"pattern":{"type":"ALTERNATING","weekA":["MONDAY"],"weekB":[]}}"""
            val noDays = """{"cooks":true,"pattern":{"type":"ALTERNATING","weekA":[],"weekB":[],"referenceWeek":"2026-10-19"}}"""
            val notMonday =
                """{"cooks":true,"pattern":{"type":"ALTERNATING","weekA":["MONDAY"],"weekB":[],"referenceWeek":"2026-10-20"}}"""

            suspend fun put(
                memberId: String,
                body: String,
            ) = client.call(HttpMethod.Put, "$PROFILES/$memberId", family.parentToken, body).status

            val statuses =
                listOf(
                    put(family.childId, noReference),
                    put(family.childId, noDays),
                    put(family.childId, notMonday),
                    put(parentId, ALTERNATING_PROFILE),
                    put("00000000-0000-4000-8000-000000000000", ALTERNATING_PROFILE),
                )

            assertEquals(
                listOf(
                    HttpStatusCode.BadRequest,
                    HttpStatusCode.BadRequest,
                    HttpStatusCode.BadRequest,
                    HttpStatusCode.BadRequest,
                    HttpStatusCode.NotFound,
                ),
                statuses,
            )
        }

    @Test
    fun `REQ-MEAL-011 exception validation returns 400 and 404`() =
        testApplication {
            start()
            val family = setupFamilyWithChild()

            suspend fun create(
                body: String,
                invalid: Boolean = false,
            ) = client.call(
                HttpMethod.Post,
                "$PROFILES/${family.childId}/exceptions",
                family.parentToken,
                body,
                invalidRequestExpected = invalid,
            )

            val reversed = create("""{"from":"2026-12-05","to":"2026-12-01","presence":"ABSENT"}""")
            val badDate = create("""{"from":"morgen","to":"2026-12-01","presence":"ABSENT"}""", invalid = true)
            val longNote =
                create("""{"from":"2026-12-01","to":"2026-12-01","presence":"ABSENT","note":"${"x".repeat(61)}"}""", invalid = true)
            val unknownException =
                client.call(HttpMethod.Delete, "/api/v1/meals/exceptions/00000000-0000-4000-8000-000000000000", family.parentToken)
            val unknownMember =
                client.call(
                    HttpMethod.Get,
                    "$PROFILES/00000000-0000-4000-8000-000000000000/exceptions",
                    family.parentToken,
                )

            assertEquals(
                listOf(
                    HttpStatusCode.BadRequest,
                    HttpStatusCode.BadRequest,
                    HttpStatusCode.BadRequest,
                    HttpStatusCode.NotFound,
                    HttpStatusCode.NotFound,
                ),
                listOf(reversed.status, badDate.status, longNote.status, unknownException.status, unknownMember.status),
            )
        }
}

private const val DAY_OF_MONTH_START = 8
