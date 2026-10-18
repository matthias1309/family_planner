package de.familyplanner.android.feature.meals.data.cooking

import de.familyplanner.android.feature.meals.data.mapAllOrNull
import de.familyplanner.android.feature.meals.data.parseDate
import de.familyplanner.android.feature.meals.data.parseInstant
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.cooking.CookStatus
import de.familyplanner.android.feature.meals.domain.cooking.WeekStatus
import de.familyplanner.api.model.CookStatus as ApiCookStatus
import de.familyplanner.api.model.WeekStatus as ApiWeekStatus

/** `submissionDeadline` ist bei nicht mehr kochenden Kindern mit Gericht `null` — gültig, kein Fehler. */
internal fun ApiCookStatus.toDomain(): CookStatus? {
    val deadline = submissionDeadline?.let { parseInstant(it) ?: return null }
    val mappedState = CookState.entries.firstOrNull { it.name == state.name } ?: return null
    return CookStatus(memberId, deadline, mappedState, dishId, late, dayValid, cancellationReason)
}

internal fun ApiWeekStatus.toDomain(): WeekStatus? {
    val dates = parseDate(weekStart)?.let { start -> parseInstant(orderDeadline)?.let { start to it } } ?: return null
    val days = openDays.mapAllOrNull { parseDate(it) } ?: return null
    val mapped = cooks.mapAllOrNull { it.toDomain() } ?: return null
    return WeekStatus(dates.first, dates.second, readyToOrder, days, mapped)
}
