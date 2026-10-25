package de.familyplanner.android.feature.gamification.domain

/** Grenzen wie im Backend (REQ-GAME-011, REQ-GAME-013). */
object PointsLimits {
    const val AMOUNT_MAX = 1000
    const val AMOUNT_DIGITS_MAX = 4
    const val REASON_MAX = 200
    const val COOKING_POINTS_MIN = 1
    const val COOKING_POINTS_MAX = 20
    const val HISTORY_LIMIT = 50
}
