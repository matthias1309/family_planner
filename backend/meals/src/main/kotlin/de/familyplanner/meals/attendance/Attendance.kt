package de.familyplanner.meals.attendance

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** All calendar and clock-time calculations of the meals module happen in this zone. */
val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

const val DAYS_PER_WEEK = 7L

/** A plan week, identified by its Monday. */
@JvmInline
value class PlanWeek(
    val monday: LocalDate,
) {
    init {
        if (monday.dayOfWeek != DayOfWeek.MONDAY) throw AppException.Validation("Woche muss mit einem Montag beginnen")
    }

    val days: List<LocalDate> get() = (0 until DAYS_PER_WEEK).map { monday.plusDays(it) }

    fun previous() = PlanWeek(monday.minusWeeks(1))

    fun next() = PlanWeek(monday.plusWeeks(1))

    companion object {
        fun containing(date: LocalDate) = PlanWeek(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))

        /** The week currently being planned: the one after today's week (Berlin time). */
        fun current(clock: Clock) = containing(LocalDate.ofInstant(clock.instant(), ZONE)).next()
    }
}

sealed interface AttendancePattern {
    data object Always : AttendancePattern

    /** [referenceWeek] is the Monday of a week of type A; weeks alternate A, B, A, ... */
    data class Alternating(
        val weekA: Set<DayOfWeek>,
        val weekB: Set<DayOfWeek>,
        val referenceWeek: PlanWeek,
    ) : AttendancePattern
}

enum class Presence { PRESENT, ABSENT }

enum class AttendanceSource { PATTERN, EXCEPTION }

@JvmInline
value class ExceptionId(
    val value: String,
)

data class AttendanceProfile(
    val memberId: MemberId,
    val cooks: Boolean,
    val pattern: AttendancePattern,
) {
    companion object {
        fun default(memberId: MemberId) = AttendanceProfile(memberId, cooks = false, pattern = AttendancePattern.Always)
    }
}

data class AttendanceException(
    val id: ExceptionId,
    val memberId: MemberId,
    val from: LocalDate,
    val to: LocalDate,
    val presence: Presence,
    val note: String?,
    val createdAt: Instant,
) {
    fun contains(date: LocalDate) = !date.isBefore(from) && !date.isAfter(to)

    fun overlaps(other: AttendanceException) = !from.isAfter(other.to) && !to.isBefore(other.from)
}

data class AttendanceDay(
    val date: LocalDate,
    val present: Boolean,
    val source: AttendanceSource,
)
