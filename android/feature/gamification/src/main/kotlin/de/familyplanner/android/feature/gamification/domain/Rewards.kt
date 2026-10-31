package de.familyplanner.android.feature.gamification.domain

import java.time.Instant

data class Reward(
    val id: String,
    val title: String,
    val emoji: String?,
    val price: Int,
    val archived: Boolean,
)

enum class RedemptionStatus { PENDING, FULFILLED, REJECTED }

data class Redemption(
    val id: String,
    val memberId: String,
    val rewardId: String,
    val title: String,
    val emoji: String?,
    val price: Int,
    val status: RedemptionStatus,
    val rejectionReason: String?,
    val requestedAt: Instant,
    val decidedAt: Instant?,
)

/** Sparziel eines Kindes; ohne Ziel sind [reward] und [progress] `null` (REQ-GAME-041). */
data class SavingsGoal(
    val memberId: String,
    val reward: Reward?,
    val balance: Int,
    val progress: BadgeProgress?,
    val redeemable: Boolean,
)

/** Eingabe „Belohnung anlegen/ändern“; [rewardId] `null` = neu. */
data class RewardDraft(
    val rewardId: String? = null,
    val title: String = "",
    val emoji: String = "",
    val priceText: String = "",
) {
    val price: Int? get() = priceText.toIntOrNull()
    val canSubmit: Boolean get() = price?.let { validateReward(title, emoji, it) == null } ?: false

    companion object {
        fun of(reward: Reward) = RewardDraft(reward.id, reward.title, reward.emoji.orEmpty(), reward.price.toString())
    }
}
