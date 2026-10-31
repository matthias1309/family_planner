package de.familyplanner.gamification.badges

import de.familyplanner.core.member.MemberId
import java.time.Instant

interface BadgeRepository {
    /** Stores the award; false (and nothing stored) if the member already has [badgeId]. */
    suspend fun award(
        memberId: MemberId,
        badgeId: String,
        at: Instant,
    ): Boolean

    /** badgeId -> awardedAt of every badge the member has. */
    suspend fun awardedOf(memberId: MemberId): Map<String, Instant>
}
