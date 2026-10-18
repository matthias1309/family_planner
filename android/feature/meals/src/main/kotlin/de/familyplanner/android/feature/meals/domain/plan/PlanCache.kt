package de.familyplanner.android.feature.meals.domain.plan

import java.time.LocalDate

/** Lokaler Lese-Cache des Essensplans (ADR-0011). Fehler beim Speichern sind nie ein Nutzerfehler. */
interface PlanCache {
    /** Gespeicherter Stand der Woche für dieses Mitglied, sonst `null`. */
    suspend fun read(
        memberId: String,
        weekStart: LocalDate,
    ): WeekPlanSnapshot?

    /** Speichert [week]; räumt Wochen vor der Vorwoche von [today] ab und behält höchstens [PlanLimits.CACHED_WEEKS_MAX]. */
    suspend fun write(
        memberId: String,
        week: WeekPlanSnapshot,
        today: LocalDate,
    )
}
