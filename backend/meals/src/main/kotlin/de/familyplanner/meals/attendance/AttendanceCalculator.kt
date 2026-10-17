package de.familyplanner.meals.attendance

import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val WEEK_TYPES = 2L

fun AttendancePattern.isPresentOn(date: LocalDate): Boolean =
    when (this) {
        AttendancePattern.Always -> true
        is AttendancePattern.Alternating -> {
            val weeksSinceReference = ChronoUnit.WEEKS.between(referenceWeek.monday, PlanWeek.containing(date).monday)
            val isWeekA = Math.floorMod(weeksSinceReference, WEEK_TYPES) == 0L
            date.dayOfWeek in if (isWeekA) weekA else weekB
        }
    }

/** An exception covering [date] wins over the pattern. */
fun attendanceOn(
    date: LocalDate,
    profile: AttendanceProfile,
    exceptions: List<AttendanceException>,
): AttendanceDay {
    val exception = exceptions.firstOrNull { it.contains(date) }
    return if (exception != null) {
        AttendanceDay(date, exception.presence == Presence.PRESENT, AttendanceSource.EXCEPTION)
    } else {
        AttendanceDay(date, profile.pattern.isPresentOn(date), AttendanceSource.PATTERN)
    }
}

fun attendanceFor(
    week: PlanWeek,
    profile: AttendanceProfile,
    exceptions: List<AttendanceException>,
): List<AttendanceDay> = week.days.map { attendanceOn(it, profile, exceptions) }
