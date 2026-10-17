package de.familyplanner.android.feature.meals.domain.attendance

/** Grenzen wie im Backend (`AttendanceService`, `RequestParsing`). */
object AttendanceLimits {
    const val EXCEPTION_MAX_DAYS = 366L
    const val NOTE_MAX = 60
    const val MIN_YEAR = 2000
    const val MAX_YEAR = 2100
}
