package de.familyplanner.android.feature.meals.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.feature.meals.data.attendance.RemoteAttendanceRepository
import de.familyplanner.android.feature.meals.domain.attendance.AttendancePattern
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceProfile
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceSource
import de.familyplanner.android.feature.meals.domain.attendance.Presence
import de.familyplanner.api.client.MealsApi
import de.familyplanner.api.client.MembersApi
import de.familyplanner.api.model.UpdateAttendanceProfileRequest
import de.familyplanner.api.model.UpdateDeadlineSettingsRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import de.familyplanner.api.model.PatternType as ApiPatternType
import de.familyplanner.api.model.Weekday as ApiWeekday

class RemoteAttendanceRepositoryTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteAttendanceRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        repository = RemoteAttendanceRepository(retrofit.create(MealsApi::class.java), retrofit.create(MembersApi::class.java), json)
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

    private fun error(
        code: String,
        message: String,
    ) = """{"success":false,"error":{"code":"$code","message":"$message"}}"""

    private val alwaysProfile = """{"memberId":"m-1","cooks":true,"pattern":{"type":"ALWAYS","weekA":[],"weekB":[],"referenceWeek":null}}"""
    private val alternatingProfile =
        """{"memberId":"m-2","cooks":false,"pattern":{"type":"ALTERNATING",
        "weekA":["TUESDAY","WEDNESDAY"],"weekB":["TUESDAY"],"referenceWeek":"2026-10-05"}}"""

    @Test
    fun `REQ-MEAL-010 profiles map always and alternating patterns`() =
        runTest {
            enqueue(200, ok("[$alwaysProfile,$alternatingProfile]"))

            val result = repository.profiles()

            assertEquals("/api/v1/meals/profiles", server.takeRequest().url.encodedPath)
            val profiles = (result as ApiResult.Success).data
            assertEquals(AttendancePattern.Always, profiles[0].pattern)
            assertEquals(true, profiles[0].cooks)
            assertEquals(
                AttendancePattern.Alternating(
                    setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
                    setOf(DayOfWeek.TUESDAY),
                    LocalDate.of(2026, 10, 5),
                ),
                profiles[1].pattern,
            )
        }

    @Test
    fun `REQ-MEAL-010 save profile sends pattern with reference week`() =
        runTest {
            enqueue(200, ok(alternatingProfile))
            val profile =
                AttendanceProfile(
                    "m-2",
                    false,
                    AttendancePattern.Alternating(setOf(DayOfWeek.TUESDAY), setOf(DayOfWeek.FRIDAY), LocalDate.of(2026, 10, 5)),
                )

            val result = repository.saveProfile(profile)

            assertIs<ApiResult.Success<*>>(result)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/meals/profiles/m-2", request.url.encodedPath)
            val body = json.decodeFromString<UpdateAttendanceProfileRequest>(request.body!!.utf8())
            assertEquals(ApiPatternType.ALTERNATING, body.pattern.type)
            assertEquals(setOf(ApiWeekday.TUESDAY), body.pattern.weekA)
            assertEquals(setOf(ApiWeekday.FRIDAY), body.pattern.weekB)
            assertEquals("2026-10-05", body.pattern.referenceWeek)
            assertEquals(false, body.cooks)
        }

    @Test
    fun `REQ-MEAL-010 always pattern is sent without days and reference`() =
        runTest {
            enqueue(200, ok(alwaysProfile))

            repository.saveProfile(AttendanceProfile("m-1", true, AttendancePattern.Always))

            val body = json.decodeFromString<UpdateAttendanceProfileRequest>(server.takeRequest().body!!.utf8())
            assertEquals(ApiPatternType.ALWAYS, body.pattern.type)
            assertEquals(emptySet(), body.pattern.weekA)
            assertEquals(emptySet(), body.pattern.weekB)
            assertNull(body.pattern.referenceWeek)
        }

    @Test
    fun `REQ-MEAL-010 alternating without reference week is a server error`() =
        runTest {
            enqueue(
                200,
                ok(
                    """[{"memberId":"m-2","cooks":true,"pattern":{"type":"ALTERNATING",
                    "weekA":["MONDAY"],"weekB":[],"referenceWeek":null}}]""",
                ),
            )

            val result = repository.profiles()

            assertEquals(AppError.Server, (result as ApiResult.Failure).error)
        }

    @Test
    fun `REQ-MEAL-010 child receives forbidden on profile update`() =
        runTest {
            enqueue(403, error("FORBIDDEN", "Nur für Eltern"))

            val result = repository.saveProfile(AttendanceProfile("m-1", true, AttendancePattern.Always))

            assertEquals(AppError.Forbidden, (result as ApiResult.Failure).error)
        }

    @Test
    fun `validation error keeps server message`() =
        runTest {
            enqueue(400, error("VALIDATION_FAILED", "Das Muster braucht mindestens einen Tag"))

            val result = repository.saveProfile(AttendanceProfile("m-1", true, AttendancePattern.Always))

            assertEquals(AppError.Validation("Das Muster braucht mindestens einen Tag"), (result as ApiResult.Failure).error)
        }

    @Test
    fun `network failure maps to Network`() =
        runTest {
            server.close()

            val result = repository.profiles()

            assertEquals(AppError.Network, (result as ApiResult.Failure).error)
        }

    @Test
    fun `REQ-MEAL-011 add exception posts dates presence and note`() =
        runTest {
            enqueue(
                201,
                ok(
                    """{"id":"e-1","memberId":"m-1","from":"2026-12-20","to":"2027-01-06","presence":"PRESENT",
                    "note":"Ferien","createdAt":"2026-10-17T10:00:00Z"}""",
                ),
            )

            val result = repository.addException("m-1", LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 6), Presence.PRESENT, "Ferien")

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/meals/profiles/m-1/exceptions", request.url.encodedPath)
            assertEquals("""{"from":"2026-12-20","to":"2027-01-06","presence":"PRESENT","note":"Ferien"}""", request.body?.utf8())
            val exception = (result as ApiResult.Success).data
            assertEquals("e-1", exception.id)
            assertEquals(LocalDate.of(2027, 1, 6), exception.to)
            assertEquals("Ferien", exception.note)
        }

    @Test
    fun `REQ-MEAL-011 overlapping exception maps 409 to conflict with server message`() =
        runTest {
            enqueue(409, error("CONFLICT", "Zeitraum überschneidet sich mit einer bestehenden Ausnahme"))

            val result = repository.addException("m-1", LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 6), Presence.ABSENT, null)

            assertEquals(
                AppError.Conflict("Zeitraum überschneidet sich mit einer bestehenden Ausnahme"),
                (result as ApiResult.Failure).error,
            )
        }

    @Test
    fun `REQ-MEAL-011 list exceptions maps entries`() =
        runTest {
            enqueue(
                200,
                ok(
                    """[{"id":"e-1","memberId":"m-1","from":"2026-12-20","to":"2026-12-24","presence":"ABSENT",
                    "createdAt":"2026-10-17T10:00:00Z"}]""",
                ),
            )

            val result = repository.exceptions("m-1")

            assertEquals("/api/v1/meals/profiles/m-1/exceptions", server.takeRequest().url.encodedPath)
            val exception = (result as ApiResult.Success).data.single()
            assertEquals(Presence.ABSENT, exception.presence)
            assertNull(exception.note)
        }

    @Test
    fun `REQ-MEAL-011 delete exception uses exception path and 204`() =
        runTest {
            enqueue(204)

            val result = repository.deleteException("e-1")

            assertIs<ApiResult.Success<*>>(result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/meals/exceptions/e-1", request.url.encodedPath)
        }

    @Test
    fun `REQ-MEAL-012 week attendance maps days and submission deadline`() =
        runTest {
            enqueue(
                200,
                ok(
                    """{"weekStart":"2026-10-19","orderDeadline":"2026-10-18T14:00:00Z","members":[
                        {"memberId":"m-1","cooks":true,"submissionDeadline":"2026-10-15T14:00:00Z","days":[
                        {"date":"2026-10-19","present":true,"source":"PATTERN"},
                        {"date":"2026-10-20","present":false,"source":"EXCEPTION"}]}]}""",
                ),
            )

            val result = repository.week(LocalDate.of(2026, 10, 19))

            assertEquals("/api/v1/meals/weeks/2026-10-19/attendance", server.takeRequest().url.encodedPath)
            val week = (result as ApiResult.Success).data
            assertEquals(Instant.parse("2026-10-18T14:00:00Z"), week.orderDeadline)
            val child = week.children.single()
            assertEquals(Instant.parse("2026-10-15T14:00:00Z"), child.submissionDeadline)
            assertEquals(AttendanceSource.EXCEPTION, child.days[1].source)
            assertEquals(false, child.days[1].present)
        }

    @Test
    fun `REQ-MEAL-012 missing submission deadline stays null`() =
        runTest {
            enqueue(
                200,
                ok(
                    """{"weekStart":"2026-10-19","orderDeadline":"2026-10-18T14:00:00Z","members":[
                        {"memberId":"m-1","cooks":true,"days":[{"date":"2026-10-19","present":false,"source":"PATTERN"}]}]}""",
                ),
            )

            val result = repository.week(LocalDate.of(2026, 10, 19))

            assertNull(
                (result as ApiResult.Success)
                    .data.children
                    .single()
                    .submissionDeadline,
            )
        }

    @Test
    fun `member names are mapped by id`() =
        runTest {
            enqueue(200, ok("""[{"id":"m-1","name":"Ben","role":"CHILD","createdAt":"2026-10-12T10:00:00Z"}]"""))

            val result = repository.memberNames()

            assertEquals(mapOf("m-1" to "Ben"), (result as ApiResult.Success).data)
        }

    private val settings =
        """{"current":{"orderDeadlineDay":"SUNDAY","orderDeadlineTime":"16:00","childDeadlineTime":"07:05","effectiveFrom":"2026-10-05"},
        "scheduled":{"orderDeadlineDay":"SATURDAY","orderDeadlineTime":"18:00","childDeadlineTime":"16:00","effectiveFrom":"2026-10-26"}}"""

    @Test
    fun `REQ-MEAL-013 settings map current and scheduled`() =
        runTest {
            enqueue(200, ok(settings))

            val overview = (repository.deadlineSettings() as ApiResult.Success).data

            assertEquals("/api/v1/meals/settings", server.takeRequest().url.encodedPath)
            assertEquals(DayOfWeek.SUNDAY, overview.current.orderDay)
            assertEquals(LocalTime.of(7, 5), overview.current.childTime)
            assertEquals(DayOfWeek.SATURDAY, overview.scheduled?.orderDay)
            assertEquals(LocalDate.of(2026, 10, 26), overview.scheduled?.effectiveFrom)
        }

    @Test
    fun `REQ-MEAL-013 settings without scheduled change keep it null`() =
        runTest {
            enqueue(
                200,
                ok(
                    """{"current":{"orderDeadlineDay":"SUNDAY","orderDeadlineTime":"16:00","childDeadlineTime":"16:00",
                    "effectiveFrom":"2026-10-05"}}""",
                ),
            )

            assertNull((repository.deadlineSettings() as ApiResult.Success).data.scheduled)
        }

    @Test
    fun `REQ-MEAL-013 save settings sends HH mm times`() =
        runTest {
            enqueue(200, ok(settings))

            repository.saveDeadlineSettings(DayOfWeek.SATURDAY, LocalTime.of(18, 0), LocalTime.of(7, 5))

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            val body = json.decodeFromString<UpdateDeadlineSettingsRequest>(request.body!!.utf8())
            assertEquals(ApiWeekday.SATURDAY, body.orderDeadlineDay)
            assertEquals("18:00", body.orderDeadlineTime)
            assertEquals("07:05", body.childDeadlineTime)
        }

    @Test
    fun `REQ-MEAL-013 unreadable time is a server error`() =
        runTest {
            enqueue(
                200,
                ok(
                    """{"current":{"orderDeadlineDay":"SUNDAY","orderDeadlineTime":"abends","childDeadlineTime":"16:00",
                    "effectiveFrom":"2026-10-05"}}""",
                ),
            )

            assertEquals(AppError.Server, (repository.deadlineSettings() as ApiResult.Failure).error)
        }
}
