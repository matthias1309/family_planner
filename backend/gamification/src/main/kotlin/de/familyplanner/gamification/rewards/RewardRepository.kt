package de.familyplanner.gamification.rewards

import de.familyplanner.core.member.MemberId
import java.time.Instant

interface RewardRepository {
    /** Sorted by price, then title (case-insensitive). */
    suspend fun findAll(includeArchived: Boolean): List<Reward>

    suspend fun findById(id: RewardId): Reward?

    suspend fun create(
        reward: Reward,
        by: MemberId,
        at: Instant,
    )

    /** Returns `false` if the reward is unknown or archived. */
    suspend fun update(
        reward: Reward,
        at: Instant,
    ): Boolean

    /** Archiving twice is fine. Returns `false` only if the reward is unknown. */
    suspend fun archive(
        id: RewardId,
        at: Instant,
    ): Boolean

    suspend fun goalOf(memberId: MemberId): RewardId?

    /** Upsert: a child has at most one goal. */
    suspend fun setGoal(
        memberId: MemberId,
        rewardId: RewardId,
        at: Instant,
    )

    suspend fun clearGoal(memberId: MemberId)
}
