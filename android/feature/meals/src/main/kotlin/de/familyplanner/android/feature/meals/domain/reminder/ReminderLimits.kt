package de.familyplanner.android.feature.meals.domain.reminder

object ReminderLimits {
    /** 72/48/24 h vor der Frist, zur Frist-Uhrzeit (REQ-MEAL-050). */
    val DAYS_BEFORE: List<Long> = listOf(3L, 2L, 1L)

    const val SYNC_INTERVAL_MINUTES = 30L
}
