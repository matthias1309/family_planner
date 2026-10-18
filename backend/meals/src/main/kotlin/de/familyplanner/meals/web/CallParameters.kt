package de.familyplanner.meals.web

import de.familyplanner.core.error.AppException
import de.familyplanner.meals.attendance.PlanWeek
import io.ktor.server.application.ApplicationCall

/** Reads the `weekStart` path parameter as the Monday of a plan week. */
internal fun ApplicationCall.weekParameter(): PlanWeek {
    val raw = parameters["weekStart"] ?: throw AppException.Validation("Ungültiges Datum 'weekStart'")
    return PlanWeek(parseDate(raw, "weekStart"))
}
