package de.familyplanner.gamification.persistence

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.gamification.ledger.EntryKind
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.LedgerEntryId
import de.familyplanner.gamification.ledger.rewardRefundSourceKey
import de.familyplanner.gamification.ledger.rewardSourceKey
import de.familyplanner.gamification.rewards.DecisionOutcome
import de.familyplanner.gamification.rewards.RedeemOutcome
import de.familyplanner.gamification.rewards.Redemption
import de.familyplanner.gamification.rewards.RedemptionId
import de.familyplanner.gamification.rewards.RedemptionRepository
import de.familyplanner.gamification.rewards.RedemptionStatus
import de.familyplanner.gamification.rewards.RewardId
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

/**
 * Every multi-table write runs in a single [dbQuery] transaction. Ledger rows are inserted with a plain `insert` (not
 * `insertIgnore`) so that a failure rolls everything back instead of being swallowed.
 */
class ExposedRedemptionRepository(
    private val db: Database,
) : RedemptionRepository {
    override suspend fun redeem(
        id: RedemptionId,
        memberId: MemberId,
        rewardId: RewardId,
        debitEntryId: String,
        at: Instant,
    ): RedeemOutcome =
        dbQuery(db) {
            val reward =
                RewardTable.selectAll().where { RewardTable.id eq rewardId.value }.firstOrNull()
                    ?: return@dbQuery RedeemOutcome.RewardNotFound
            if (reward[RewardTable.archivedAt] != null) return@dbQuery RedeemOutcome.RewardArchived
            val price = reward[RewardTable.price]
            val balance = balanceOf(memberId)
            if (balance < price) return@dbQuery RedeemOutcome.InsufficientBalance(balance, price)
            val title = reward[RewardTable.title]
            insertRedemption(id, memberId, rewardId, reward, at)
            val debitTitle = "Belohnung: $title"
            insertEntry(
                LedgerEntry(
                    LedgerEntryId(debitEntryId),
                    memberId,
                    EntryKind.REWARD,
                    -price,
                    debitTitle,
                    null,
                    rewardSourceKey(id.value),
                    memberId,
                    at,
                ),
            )
            SavingsGoalTable.deleteWhere { (SavingsGoalTable.memberId eq memberId.value) and (SavingsGoalTable.rewardId eq rewardId.value) }
            RedeemOutcome.Redeemed(checkNotNull(rowOf(id)?.toRedemption()))
        }

    override suspend fun findById(id: RedemptionId): Redemption? = dbQuery(db) { rowOf(id)?.toRedemption() }

    override suspend fun find(
        memberId: MemberId?,
        status: RedemptionStatus?,
        limit: Int,
    ): List<Redemption> =
        dbQuery(db) {
            val ofMember = memberId?.let { RedemptionTable.memberId eq it.value } ?: Op.TRUE
            val withStatus = status?.let { RedemptionTable.status eq it.name } ?: Op.TRUE
            RedemptionTable
                .selectAll()
                .where { ofMember and withStatus }
                .orderBy(RedemptionTable.requestedAt to SortOrder.DESC, RedemptionTable.seq to SortOrder.DESC)
                .limit(limit)
                .map { it.toRedemption() }
        }

    override suspend fun fulfill(
        id: RedemptionId,
        by: MemberId,
        at: Instant,
    ): DecisionOutcome = dbQuery(db) { decide(id, by, RedemptionStatus.FULFILLED, null, at) }

    override suspend fun reject(
        id: RedemptionId,
        by: MemberId,
        reason: String,
        at: Instant,
        refundEntryId: String,
    ): DecisionOutcome =
        dbQuery(db) {
            val outcome = decide(id, by, RedemptionStatus.REJECTED, reason, at)
            if (outcome == DecisionOutcome.CHANGED) {
                val redemption = checkNotNull(rowOf(id)?.toRedemption())
                val title = "Zurückgebucht: ${redemption.title}"
                val key = rewardRefundSourceKey(id.value)
                val kind = EntryKind.REWARD_REFUND
                insertEntry(
                    LedgerEntry(LedgerEntryId(refundEntryId), redemption.memberId, kind, redemption.price, title, reason, key, by, at),
                )
            }
            outcome
        }

    private fun JdbcTransaction.decide(
        id: RedemptionId,
        by: MemberId,
        status: RedemptionStatus,
        reason: String?,
        at: Instant,
    ): DecisionOutcome {
        val changed =
            RedemptionTable.update({ (RedemptionTable.id eq id.value) and (RedemptionTable.status eq RedemptionStatus.PENDING.name) }) {
                it[RedemptionTable.status] = status.name
                it[rejectionReason] = reason
                it[decidedBy] = by.value
                it[decidedAt] = at.toEpochMilli()
            }
        if (changed > 0) return DecisionOutcome.CHANGED
        return if (rowOf(id) != null) DecisionOutcome.NOT_PENDING else DecisionOutcome.NOT_FOUND
    }

    private fun JdbcTransaction.rowOf(id: RedemptionId): ResultRow? =
        RedemptionTable.selectAll().where { RedemptionTable.id eq id.value }.firstOrNull()

    /** Balance = sum of all ledger rows of the member (see `totalsFrom`). */
    private fun JdbcTransaction.balanceOf(memberId: MemberId): Int =
        LedgerTable.selectAll().where { LedgerTable.memberId eq memberId.value }.sumOf { it[LedgerTable.amount] }

    private fun JdbcTransaction.insertRedemption(
        id: RedemptionId,
        memberId: MemberId,
        rewardId: RewardId,
        reward: ResultRow,
        at: Instant,
    ) {
        RedemptionTable.insert {
            it[RedemptionTable.id] = id.value
            it[RedemptionTable.memberId] = memberId.value
            it[RedemptionTable.rewardId] = rewardId.value
            it[title] = reward[RewardTable.title]
            it[emoji] = reward[RewardTable.emoji]
            it[price] = reward[RewardTable.price]
            it[status] = RedemptionStatus.PENDING.name
            it[requestedAt] = at.toEpochMilli()
        }
    }

    private fun JdbcTransaction.insertEntry(entry: LedgerEntry) {
        LedgerTable.insert {
            it[id] = entry.id.value
            it[memberId] = entry.memberId.value
            it[kind] = entry.kind.name
            it[amount] = entry.amount
            it[title] = entry.title
            it[note] = entry.note
            it[sourceKey] = entry.sourceKey
            it[createdBy] = entry.createdBy?.value
            it[bookedAt] = entry.bookedAt.toEpochMilli()
        }
    }
}

private fun ResultRow.toRedemption() =
    Redemption(
        id = RedemptionId(this[RedemptionTable.id]),
        memberId = MemberId(this[RedemptionTable.memberId]),
        rewardId = RewardId(this[RedemptionTable.rewardId]),
        title = this[RedemptionTable.title],
        emoji = this[RedemptionTable.emoji],
        price = this[RedemptionTable.price],
        status = RedemptionStatus.valueOf(this[RedemptionTable.status]),
        rejectionReason = this[RedemptionTable.rejectionReason],
        requestedAt = Instant.ofEpochMilli(this[RedemptionTable.requestedAt]),
        decidedAt = this[RedemptionTable.decidedAt]?.let(Instant::ofEpochMilli),
    )
