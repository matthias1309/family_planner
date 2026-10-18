package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.meals.attendance.MemberWeekAttendance
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.attendance.WeekAttendance
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Validates a child's dish against the child's attendance in the dish's plan week; [cook] is null if the child is unknown there. */
fun requireChildMayCook(
    date: LocalDate,
    cook: MemberWeekAttendance?,
) {
    if (cook?.submissionDeadline == null) throw AppException.Validation("Das Kind kocht in dieser Woche nicht")
    if (cook.days.none { it.date == date && it.present }) throw AppException.Validation("Das Kind ist an diesem Tag nicht da")
}

fun isLate(
    deadline: Instant?,
    now: Instant,
): Boolean = deadline != null && now.isAfter(deadline)

/** Children may only write for the week being planned or later weeks. */
fun requireOpenForChildren(
    week: PlanWeek,
    clock: Clock,
) {
    if (week.monday.isBefore(PlanWeek.current(clock).monday)) {
        throw AppException.Validation("Für diese Woche kann nicht mehr eingereicht werden")
    }
}

/** True unless [dish] is cooked by a child who is absent on its day. */
fun isDayValid(
    dish: Dish,
    attendance: WeekAttendance,
): Boolean {
    val cook = attendance.members.firstOrNull { it.memberId == dish.cookId } ?: return true
    return cook.days.firstOrNull { it.date == dish.date }?.present ?: true
}
