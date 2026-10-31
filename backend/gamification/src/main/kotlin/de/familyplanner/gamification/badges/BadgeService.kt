package de.familyplanner.gamification.badges

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import de.familyplanner.gamification.account.Actor
import de.familyplanner.gamification.ledger.LedgerRepository
import de.familyplanner.gamification.requireChild
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

/** One catalog entry as seen by a viewer; [masked] = secret and not earned, [progress] only in the full view. */
data class BadgeView(
    val badge: Badge,
    val masked: Boolean,
    val awardedAt: Instant?,
    val progress: Int?,
)

data class BadgeOverview(
    val memberId: MemberId,
    val earnedCount: Int,
    val totalCount: Int,
    val items: List<BadgeView>,
)

class BadgeService(
    private val members: MemberRepository,
    private val ledger: LedgerRepository,
    private val badges: BadgeRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(BadgeService::class.java)

    /** Awards every badge whose rule is met and that the child does not have yet; safe to call any time. */
    suspend fun evaluate(memberId: MemberId) {
        val stats = badgeStatsFrom(ledger.allEntriesOf(memberId))
        val owned = badges.awardedOf(memberId).keys
        Badge.entries.filter { it.id !in owned && it.isMetBy(stats) }.forEach { badge ->
            if (badges.award(memberId, badge.id, clock.instant())) {
                log.info("Badge awarded: {} to {}", badge.id, memberId.value)
            }
        }
    }

    /** Startup catch-up for every child; one failing child does not stop the others. */
    suspend fun evaluateAll() {
        members.findAll().filter { it.role == Role.CHILD }.forEach { child ->
            try {
                evaluate(child.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                log.error("Badge evaluation failed for {}", child.id.value, failure)
            }
        }
    }

    /** Own and parent view: whole catalog with progress. Sibling view: earned badges only. Never writes. */
    suspend fun overview(
        actor: Actor,
        memberId: MemberId,
    ): BadgeOverview {
        members.requireChild(memberId)
        val awarded = badges.awardedOf(memberId)
        val fullView = actor.isParent || actor.id == memberId
        val stats = if (fullView) badgeStatsFrom(ledger.allEntriesOf(memberId)) else BadgeStats.ZERO
        val items =
            Badge.entries
                .filter { fullView || it.id in awarded }
                .map { badge -> viewOf(badge, awarded[badge.id], stats, fullView) }
        val earnedCount = Badge.entries.count { it.id in awarded }
        return BadgeOverview(memberId, earnedCount, Badge.entries.size, items)
    }

    private fun viewOf(
        badge: Badge,
        awardedAt: Instant?,
        stats: BadgeStats,
        fullView: Boolean,
    ): BadgeView =
        when {
            awardedAt != null -> BadgeView(badge, masked = false, awardedAt = awardedAt, progress = null)
            badge.secret -> BadgeView(badge, masked = true, awardedAt = null, progress = null)
            else -> BadgeView(badge, masked = false, awardedAt = null, progress = if (fullView) badge.progressOf(stats) else null)
        }
}
