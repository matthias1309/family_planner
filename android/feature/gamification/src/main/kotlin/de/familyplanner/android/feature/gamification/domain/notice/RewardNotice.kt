package de.familyplanner.android.feature.gamification.domain.notice

import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import java.time.Instant

sealed interface RewardNotice {
    val redemptionId: String

    data class Requested(
        override val redemptionId: String,
        val childName: String?,
        val title: String,
        val emoji: String?,
    ) : RewardNotice

    data class Fulfilled(
        override val redemptionId: String,
        val title: String,
        val emoji: String?,
    ) : RewardNotice

    data class Rejected(
        override val redemptionId: String,
        val title: String,
        val emoji: String?,
        val reason: String?,
    ) : RewardNotice
}

/** Stand der Einlösungen beim letzten Abgleich. */
data class RedemptionSnapshot(
    val statuses: Map<String, RedemptionStatus>,
    val syncedAt: Instant,
)
