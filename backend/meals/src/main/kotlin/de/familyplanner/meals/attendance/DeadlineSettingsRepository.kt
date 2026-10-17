package de.familyplanner.meals.attendance

import java.time.Instant

interface DeadlineSettingsRepository {
    /** Settings with the greatest `effectiveFrom` that is not after [week]. */
    suspend fun findEffective(week: PlanWeek): DeadlineSettings?

    /** Settings with the smallest `effectiveFrom` after [week]. */
    suspend fun findScheduledAfter(week: PlanWeek): DeadlineSettings?

    /** Inserts or replaces the settings for their `effectiveFrom` week. */
    suspend fun save(
        settings: DeadlineSettings,
        at: Instant,
    )
}
