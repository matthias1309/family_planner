package de.familyplanner.gamification.rewards

import de.familyplanner.core.member.MemberId
import java.time.Instant

const val REJECTION_REASON_MAX = 200

@JvmInline
value class RedemptionId(
    val value: String,
)

enum class RedemptionStatus { PENDING, FULFILLED, REJECTED }

/** Snapshot of the reward at redemption time; the debit is the ledger entry with `rewardSourceKey`. */
data class Redemption(
    val id: RedemptionId,
    val memberId: MemberId,
    val rewardId: RewardId,
    val title: String,
    val emoji: String?,
    val price: Int,
    val status: RedemptionStatus,
    val rejectionReason: String?,
    val requestedAt: Instant,
    val decidedAt: Instant?,
)

/** Result of the atomic redeem in [RedemptionRepository.redeem]. */
sealed interface RedeemOutcome {
    data class Redeemed(
        val redemption: Redemption,
    ) : RedeemOutcome

    data object RewardNotFound : RedeemOutcome

    data object RewardArchived : RedeemOutcome

    data class InsufficientBalance(
        val balance: Int,
        val price: Int,
    ) : RedeemOutcome
}

enum class DecisionOutcome { CHANGED, NOT_FOUND, NOT_PENDING }
