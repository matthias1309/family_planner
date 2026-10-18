package de.familyplanner.meals.plan

import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.attendance.WeekAttendance
import java.time.Instant
import java.time.LocalDate

enum class CookState { NOT_SUBMITTED, SUBMITTED, APPROVED, REJECTED, CANCELLED }

data class CookStatus(
    val memberId: MemberId,
    val submissionDeadline: Instant?,
    val state: CookState,
    val dishId: DishId?,
    val late: Boolean,
    val dayValid: Boolean,
    val cancellationReason: String?,
)

data class WeekStatus(
    val week: PlanWeek,
    val orderDeadline: Instant,
    val openDays: List<LocalDate>,
    val cooks: List<CookStatus>,
    val readyToOrder: Boolean,
)

/** Cooks are the children cooking in the week plus children that still have a dish there. */
fun weekStatusOf(
    plan: WeekPlan,
    attendance: WeekAttendance,
    cancellations: List<Cancellation>,
): WeekStatus {
    val planned = plan.days.filter { it.dish != null }
    val dayByCook = planned.mapNotNull { day -> day.dish?.let { it.cookId to day } }.toMap()
    val cooks =
        attendance.members
            .filter { it.submissionDeadline != null || it.memberId in dayByCook }
            .map { member ->
                val day = dayByCook[member.memberId]
                val cancellation = cancellations.firstOrNull { it.memberId == member.memberId && it.week == plan.week }
                CookStatus(
                    memberId = member.memberId,
                    submissionDeadline = member.submissionDeadline,
                    state = cookState(cancellation, day?.dish),
                    dishId = day?.dish?.id,
                    late = day?.dish?.late ?: false,
                    dayValid = day?.dayValid ?: true,
                    cancellationReason = cancellation?.reason,
                )
            }
    val openDays = plan.days.filter { it.dish == null }.map { it.date }
    val ready = openDays.isEmpty() && planned.all { it.dish?.status == DishStatus.APPROVED && it.dayValid }
    return WeekStatus(plan.week, attendance.orderDeadline, openDays, cooks, ready)
}

private fun cookState(
    cancellation: Cancellation?,
    dish: Dish?,
): CookState =
    when {
        cancellation != null -> CookState.CANCELLED
        dish == null -> CookState.NOT_SUBMITTED
        else ->
            when (dish.status) {
                DishStatus.SUBMITTED -> CookState.SUBMITTED
                DishStatus.APPROVED -> CookState.APPROVED
                DishStatus.REJECTED -> CookState.REJECTED
            }
    }
