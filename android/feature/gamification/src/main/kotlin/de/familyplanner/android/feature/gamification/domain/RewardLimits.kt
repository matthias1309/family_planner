package de.familyplanner.android.feature.gamification.domain

/** Grenzen wie im Backend (REQ-GAME-040, REQ-GAME-043). */
object RewardLimits {
    const val TITLE_MAX = 40
    const val EMOJI_MAX = 16
    const val PRICE_MIN = 1
    const val PRICE_MAX = 10_000
    const val PRICE_DIGITS_MAX = 5
    const val REASON_MAX = 200
    const val CHILD_HISTORY_LIMIT = 20
    const val PENDING_LIMIT = 100
    const val SYNC_HISTORY_LIMIT = 50
    const val SYNC_INTERVAL_MINUTES = 30L
}
