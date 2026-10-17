package de.familyplanner.android.feature.meals.domain.attendance

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class WeekType { A, B }

sealed interface AttendancePattern {
    data object Always : AttendancePattern

    data class Alternating(
        val weekA: Set<DayOfWeek>,
        val weekB: Set<DayOfWeek>,
        val referenceWeek: LocalDate,
    ) : AttendancePattern
}

data class AttendanceProfile(
    val memberId: String,
    val cooks: Boolean,
    val pattern: AttendancePattern,
)

enum class Presence { PRESENT, ABSENT }

enum class AttendanceSource { PATTERN, EXCEPTION }

data class AttendanceException(
    val id: String,
    val memberId: String,
    val from: LocalDate,
    val to: LocalDate,
    val presence: Presence,
    val note: String?,
)

data class DeadlineSettings(
    val orderDay: DayOfWeek,
    val orderTime: LocalTime,
    val childTime: LocalTime,
    val effectiveFrom: LocalDate,
)

data class DeadlineSettingsOverview(
    val current: DeadlineSettings,
    val scheduled: DeadlineSettings?,
)

data class AttendanceDay(
    val date: LocalDate,
    val present: Boolean,
    val source: AttendanceSource,
)

/** Anwesenheit eines Kindes aus der API — noch ohne Namen (die liefert `GET /members`). */
data class RawChildWeek(
    val memberId: String,
    val cooks: Boolean,
    val days: List<AttendanceDay>,
    val submissionDeadline: Instant?,
)

data class WeekAttendanceRaw(
    val weekStart: LocalDate,
    val orderDeadline: Instant,
    val children: List<RawChildWeek>,
)

data class ChildWeek(
    val memberId: String,
    val name: String,
    val cooks: Boolean,
    val days: List<AttendanceDay>,
    val submissionDeadline: Instant?,
)

data class WeekOverview(
    val weekStart: LocalDate,
    val orderDeadline: Instant,
    val children: List<ChildWeek>,
)

data class ChildProfile(
    val memberId: String,
    val name: String,
    val profile: AttendanceProfile,
)
