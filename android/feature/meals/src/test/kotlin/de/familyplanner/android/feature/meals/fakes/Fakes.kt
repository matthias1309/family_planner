package de.familyplanner.android.feature.meals.fakes

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceDay
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceException
import de.familyplanner.android.feature.meals.domain.attendance.AttendancePattern
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceProfile
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceRepository
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceSource
import de.familyplanner.android.feature.meals.domain.attendance.DeadlineSettings
import de.familyplanner.android.feature.meals.domain.attendance.DeadlineSettingsOverview
import de.familyplanner.android.feature.meals.domain.attendance.Presence
import de.familyplanner.android.feature.meals.domain.attendance.RawChildWeek
import de.familyplanner.android.feature.meals.domain.attendance.WeekAttendanceRaw
import kotlinx.coroutines.CompletableDeferred
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

val parentSession = Session("tok", "p-1", "Anna", Role.PARENT, "d-1")
val childSession = Session("tok", "c-1", "Ben", Role.CHILD, "d-2")

val planWeek: LocalDate = LocalDate.of(2026, 10, 19)
val someException = AttendanceException("e-1", "c-1", LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 6), Presence.PRESENT, "Ferien")
val someSettings = DeadlineSettings(DayOfWeek.SUNDAY, LocalTime.of(16, 0), LocalTime.of(16, 0), LocalDate.of(2026, 10, 5))
val alternatingPattern =
    AttendancePattern.Alternating(
        setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
        setOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY),
        LocalDate.of(2026, 10, 12),
    )

fun weekOf(
    vararg children: RawChildWeek,
    start: LocalDate = planWeek,
) = WeekAttendanceRaw(start, Instant.parse("2026-10-18T14:00:00Z"), children.toList())

fun childWeek(
    memberId: String = "c-1",
    cooks: Boolean = true,
    present: Boolean = true,
    deadline: Instant? = Instant.parse("2026-10-15T14:00:00Z"),
) = RawChildWeek(
    memberId,
    cooks,
    (0L..6L).map { AttendanceDay(planWeek.plusDays(it), present, AttendanceSource.PATTERN) },
    deadline,
)

class FakeAttendanceRepository : AttendanceRepository {
    var namesResult: ApiResult<Map<String, String>> = ApiResult.Success(mapOf("c-1" to "Ben", "c-2" to "Tim"))
    var profilesResult: ApiResult<List<AttendanceProfile>> =
        ApiResult.Success(
            listOf(
                AttendanceProfile("c-1", true, alternatingPattern),
                AttendanceProfile("c-2", false, AttendancePattern.Always),
            ),
        )
    var saveProfileResult: ApiResult<AttendanceProfile>? = null
    var exceptionsResult: ApiResult<List<AttendanceException>> = ApiResult.Success(emptyList())
    var addExceptionResult: ApiResult<AttendanceException> = ApiResult.Success(someException)
    var deleteExceptionResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var settingsResult: ApiResult<DeadlineSettingsOverview> = ApiResult.Success(DeadlineSettingsOverview(someSettings, null))
    var saveSettingsResult: ApiResult<DeadlineSettingsOverview>? = null
    var weekResult: (LocalDate) -> ApiResult<WeekAttendanceRaw> = { ApiResult.Success(weekOf(childWeek(), start = it)) }

    val savedProfiles = mutableListOf<AttendanceProfile>()
    val addedExceptions = mutableListOf<List<Any?>>()
    val deletedExceptions = mutableListOf<String>()
    val savedSettings = mutableListOf<List<Any>>()
    val requestedWeeks = mutableListOf<LocalDate>()

    /** Hält die Antwort für eine Woche zurück, bis das Gate geöffnet wird (Wochenwechsel-Rennen). */
    val weekGates = mutableMapOf<LocalDate, CompletableDeferred<Unit>>()

    override suspend fun memberNames() = namesResult

    override suspend fun profiles() = profilesResult

    override suspend fun saveProfile(profile: AttendanceProfile): ApiResult<AttendanceProfile> {
        savedProfiles += profile
        return saveProfileResult ?: ApiResult.Success(profile)
    }

    override suspend fun exceptions(memberId: String) = exceptionsResult

    override suspend fun addException(
        memberId: String,
        from: LocalDate,
        to: LocalDate,
        presence: Presence,
        note: String?,
    ): ApiResult<AttendanceException> {
        addedExceptions += listOf(memberId, from, to, presence, note)
        return addExceptionResult
    }

    override suspend fun deleteException(exceptionId: String): ApiResult<Unit> {
        deletedExceptions += exceptionId
        return deleteExceptionResult
    }

    override suspend fun deadlineSettings() = settingsResult

    override suspend fun saveDeadlineSettings(
        orderDay: DayOfWeek,
        orderTime: LocalTime,
        childTime: LocalTime,
    ): ApiResult<DeadlineSettingsOverview> {
        savedSettings += listOf(orderDay, orderTime, childTime)
        return saveSettingsResult ?: ApiResult.Success(
            DeadlineSettingsOverview(someSettings, DeadlineSettings(orderDay, orderTime, childTime, LocalDate.of(2026, 10, 26))),
        )
    }

    override suspend fun week(weekStart: LocalDate): ApiResult<WeekAttendanceRaw> {
        requestedWeeks += weekStart
        weekGates[weekStart]?.await()
        return weekResult(weekStart)
    }
}
