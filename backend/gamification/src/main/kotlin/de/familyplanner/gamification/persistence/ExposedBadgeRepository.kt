package de.familyplanner.gamification.persistence

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.gamification.badges.BadgeRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant

class ExposedBadgeRepository(
    private val db: Database,
) : BadgeRepository {
    /** `INSERT OR IGNORE` also hides FK violations, so callers only award for existing children. */
    override suspend fun award(
        memberId: MemberId,
        badgeId: String,
        at: Instant,
    ): Boolean =
        dbQuery(db) {
            val statement =
                BadgeTable.insertIgnore {
                    it[BadgeTable.memberId] = memberId.value
                    it[BadgeTable.badgeId] = badgeId
                    it[awardedAt] = at.toEpochMilli()
                }
            statement.insertedCount > 0
        }

    override suspend fun awardedOf(memberId: MemberId): Map<String, Instant> =
        dbQuery(db) {
            BadgeTable
                .selectAll()
                .where { BadgeTable.memberId eq memberId.value }
                .associate { it[BadgeTable.badgeId] to Instant.ofEpochMilli(it[BadgeTable.awardedAt]) }
        }
}
