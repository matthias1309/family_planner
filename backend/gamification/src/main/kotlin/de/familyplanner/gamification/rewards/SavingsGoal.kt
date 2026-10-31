package de.familyplanner.gamification.rewards

import de.familyplanner.core.member.MemberId

/** Goal of one child with progress; [reward] null = no goal (REQ-GAME-041). */
data class SavingsGoal(
    val memberId: MemberId,
    val reward: Reward?,
    val balance: Int,
) {
    /** Balance clamped to 0..price; null without a goal. */
    val progress: Int? get() = reward?.let { balance.coerceIn(0, it.price) }

    val redeemable: Boolean get() = reward != null && !reward.archived && balance >= reward.price
}
