package de.familyplanner.gamification.persistence

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.gamification.rewards.Reward
import de.familyplanner.gamification.rewards.RewardId
import de.familyplanner.gamification.rewards.RewardRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant

class ExposedRewardRepository(
    private val db: Database,
) : RewardRepository {
    override suspend fun findAll(includeArchived: Boolean): List<Reward> =
        dbQuery(db) {
            val rows = RewardTable.selectAll()
            val visible = if (includeArchived) rows else rows.where { RewardTable.archivedAt.isNull() }
            visible.map { it.toReward() }.sortedWith(compareBy<Reward> { it.price }.thenBy { it.title.lowercase() })
        }

    override suspend fun findById(id: RewardId): Reward? =
        dbQuery(db) {
            RewardTable
                .selectAll()
                .where { RewardTable.id eq id.value }
                .firstOrNull()
                ?.toReward()
        }

    override suspend fun create(
        reward: Reward,
        by: MemberId,
        at: Instant,
    ) {
        dbQuery(db) {
            RewardTable.insert {
                it[id] = reward.id.value
                it[title] = reward.title
                it[emoji] = reward.emoji
                it[price] = reward.price
                it[createdBy] = by.value
                it[createdAt] = at.toEpochMilli()
                it[updatedAt] = at.toEpochMilli()
            }
        }
    }

    override suspend fun update(
        reward: Reward,
        at: Instant,
    ): Boolean =
        dbQuery(db) {
            val changed =
                RewardTable.update({ (RewardTable.id eq reward.id.value) and RewardTable.archivedAt.isNull() }) {
                    it[title] = reward.title
                    it[emoji] = reward.emoji
                    it[price] = reward.price
                    it[updatedAt] = at.toEpochMilli()
                }
            changed > 0
        }

    override suspend fun archive(
        id: RewardId,
        at: Instant,
    ): Boolean =
        dbQuery(db) {
            RewardTable.update({ (RewardTable.id eq id.value) and RewardTable.archivedAt.isNull() }) {
                it[archivedAt] = at.toEpochMilli()
                it[updatedAt] = at.toEpochMilli()
            }
            RewardTable.selectAll().where { RewardTable.id eq id.value }.any()
        }

    override suspend fun goalOf(memberId: MemberId): RewardId? =
        dbQuery(db) {
            SavingsGoalTable
                .selectAll()
                .where { SavingsGoalTable.memberId eq memberId.value }
                .firstOrNull()
                ?.let { RewardId(it[SavingsGoalTable.rewardId]) }
        }

    override suspend fun setGoal(
        memberId: MemberId,
        rewardId: RewardId,
        at: Instant,
    ) {
        dbQuery(db) {
            SavingsGoalTable.upsert {
                it[SavingsGoalTable.memberId] = memberId.value
                it[SavingsGoalTable.rewardId] = rewardId.value
                it[setAt] = at.toEpochMilli()
            }
        }
    }

    override suspend fun clearGoal(memberId: MemberId) {
        dbQuery(db) { SavingsGoalTable.deleteWhere { SavingsGoalTable.memberId eq memberId.value } }
    }
}

private fun ResultRow.toReward() =
    Reward(
        id = RewardId(this[RewardTable.id]),
        title = this[RewardTable.title],
        emoji = this[RewardTable.emoji],
        price = this[RewardTable.price],
        archived = this[RewardTable.archivedAt] != null,
    )
