package de.familyplanner.gamification.rewards

import de.familyplanner.core.member.MemberId
import java.time.Instant

interface RedemptionRepository {
    /**
     * In one transaction: reward exists and is active, balance >= price, inserts the PENDING redemption, books the
     * REWARD debit (title "Belohnung: <title>", source_key reward:<id>) and clears the child's goal if it is this reward.
     */
    suspend fun redeem(
        id: RedemptionId,
        memberId: MemberId,
        rewardId: RewardId,
        debitEntryId: String,
        at: Instant,
    ): RedeemOutcome

    suspend fun findById(id: RedemptionId): Redemption?

    /** Newest first (requestedAt, then insertion order); [memberId] null = all children, [status] null = all. */
    suspend fun find(
        memberId: MemberId?,
        status: RedemptionStatus?,
        limit: Int,
    ): List<Redemption>

    /** PENDING -> FULFILLED. */
    suspend fun fulfill(
        id: RedemptionId,
        by: MemberId,
        at: Instant,
    ): DecisionOutcome

    /** PENDING -> REJECTED and, in the same transaction, the REWARD_REFUND (+price, note = reason). */
    suspend fun reject(
        id: RedemptionId,
        by: MemberId,
        reason: String,
        at: Instant,
        refundEntryId: String,
    ): DecisionOutcome
}
