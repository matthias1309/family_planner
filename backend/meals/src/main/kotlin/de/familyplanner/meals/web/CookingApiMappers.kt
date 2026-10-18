package de.familyplanner.meals.web

import de.familyplanner.meals.plan.Cancellation
import de.familyplanner.meals.plan.CookStatus
import de.familyplanner.meals.plan.WeekStatus
import de.familyplanner.api.model.CookState as ApiCookState
import de.familyplanner.api.model.CookStatus as ApiCookStatus
import de.familyplanner.api.model.CookingCancellation as ApiCookingCancellation
import de.familyplanner.api.model.WeekStatus as ApiWeekStatus

fun Cancellation.toApi() = ApiCookingCancellation(memberId = memberId.value, weekStart = week.monday.toString(), reason = reason)

fun CookStatus.toApi() =
    ApiCookStatus(
        memberId = memberId.value,
        state = ApiCookState.valueOf(state.name),
        late = late,
        dayValid = dayValid,
        submissionDeadline = submissionDeadline?.toString(),
        dishId = dishId?.value,
        cancellationReason = cancellationReason,
    )

fun WeekStatus.toApi() =
    ApiWeekStatus(
        weekStart = week.monday.toString(),
        orderDeadline = orderDeadline.toString(),
        readyToOrder = readyToOrder,
        openDays = openDays.map { it.toString() },
        cooks = cooks.map { it.toApi() },
    )
