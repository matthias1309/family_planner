package de.familyplanner.meals.attendance

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

val DEFAULT_DEADLINE_TIME: LocalTime = LocalTime.of(16, 0)
val DEFAULT_ORDER_DEADLINE_DAY: DayOfWeek = DayOfWeek.SUNDAY

data class DeadlineSettings(
    val orderDeadlineDay: DayOfWeek,
    val orderDeadlineTime: LocalTime,
    val childDeadlineTime: LocalTime,
    val effectiveFrom: PlanWeek,
) {
    companion object {
        fun defaults(effectiveFrom: PlanWeek) =
            DeadlineSettings(DEFAULT_ORDER_DEADLINE_DAY, DEFAULT_DEADLINE_TIME, DEFAULT_DEADLINE_TIME, effectiveFrom)
    }
}

private fun instantAt(
    date: LocalDate,
    time: LocalTime,
): Instant = ZonedDateTime.of(date, time, ZONE).toInstant()

/** Deadline for ordering the groceries of [week]; it falls into the running week before it. */
fun orderDeadline(
    week: PlanWeek,
    settings: DeadlineSettings,
): Instant = instantAt(week.previous().monday.plusDays((settings.orderDeadlineDay.value - 1).toLong()), settings.orderDeadlineTime)

/**
 * Deadline by which a cooking child must submit a dish for [week]: the last present day of the running week at the
 * child deadline time, but never later than the order deadline. Null if the child does not cook or is absent all week.
 */
fun submissionDeadline(
    week: PlanWeek,
    settings: DeadlineSettings,
    profile: AttendanceProfile,
    exceptions: List<AttendanceException>,
): Instant? {
    if (!profile.cooks) return null
    if (attendanceFor(week, profile, exceptions).none { it.present }) return null
    val order = orderDeadline(week, settings)
    val lastPresentDay = attendanceFor(week.previous(), profile, exceptions).lastOrNull { it.present } ?: return order
    return minOf(instantAt(lastPresentDay.date, settings.childDeadlineTime), order)
}
