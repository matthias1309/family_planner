package de.familyplanner.gamification.fakes

import de.familyplanner.core.member.MemberId
import de.familyplanner.gamification.badges.BadgeRepository
import de.familyplanner.gamification.ledger.EntryKind
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.LedgerEntryId
import de.familyplanner.gamification.ledger.LedgerRepository
import de.familyplanner.gamification.ledger.Totals
import de.familyplanner.gamification.ledger.rewardRefundSourceKey
import de.familyplanner.gamification.ledger.rewardSourceKey
import de.familyplanner.gamification.ledger.totalsFrom
import de.familyplanner.gamification.rewards.DecisionOutcome
import de.familyplanner.gamification.rewards.RedeemOutcome
import de.familyplanner.gamification.rewards.Redemption
import de.familyplanner.gamification.rewards.RedemptionId
import de.familyplanner.gamification.rewards.RedemptionRepository
import de.familyplanner.gamification.rewards.RedemptionStatus
import de.familyplanner.gamification.rewards.Reward
import de.familyplanner.gamification.rewards.RewardId
import de.familyplanner.gamification.rewards.RewardRepository
import de.familyplanner.gamification.settings.GameSettings
import de.familyplanner.gamification.settings.GameSettingsRepository
import java.time.Instant

class InMemoryLedgerRepository : LedgerRepository {
    val entries = mutableListOf<LedgerEntry>()

    override suspend fun append(entry: LedgerEntry): Boolean {
        if (entry.sourceKey != null && entries.any { it.sourceKey == entry.sourceKey }) return false
        entries += entry
        return true
    }

    override suspend fun totalsOf(memberId: MemberId): Totals = totals(entries.filter { it.memberId == memberId })

    override suspend fun allTotals(): Map<MemberId, Totals> = entries.groupBy { it.memberId }.mapValues { totals(it.value) }

    override suspend fun entriesOf(
        memberId: MemberId,
        limit: Int,
    ): List<LedgerEntry> =
        entries
            .withIndex()
            .filter { it.value.memberId == memberId }
            .sortedWith(compareByDescending<IndexedValue<LedgerEntry>> { it.value.bookedAt }.thenByDescending { it.index })
            .take(limit)
            .map { it.value }

    override suspend fun allEntriesOf(memberId: MemberId): List<LedgerEntry> = entries.filter { it.memberId == memberId }

    private fun totals(rows: List<LedgerEntry>) = totalsFrom(rows.map { it.kind to it.amount })
}

class InMemoryGameSettingsRepository : GameSettingsRepository {
    private var stored: GameSettings? = null

    override suspend fun find(): GameSettings? = stored

    override suspend fun save(
        settings: GameSettings,
        at: Instant,
    ) {
        stored = settings
    }
}

class InMemoryBadgeRepository : BadgeRepository {
    val awards = mutableMapOf<Pair<MemberId, String>, Instant>()

    override suspend fun award(
        memberId: MemberId,
        badgeId: String,
        at: Instant,
    ): Boolean = awards.putIfAbsent(memberId to badgeId, at) == null

    override suspend fun awardedOf(memberId: MemberId): Map<String, Instant> =
        awards.filterKeys { it.first == memberId }.mapKeys { it.key.second }
}

class InMemoryRewardRepository : RewardRepository {
    private val stored = mutableMapOf<RewardId, Reward>()
    private val goals = mutableMapOf<MemberId, RewardId>()

    override suspend fun findAll(includeArchived: Boolean): List<Reward> =
        stored.values
            .filter { includeArchived || !it.archived }
            .sortedWith(compareBy<Reward> { it.price }.thenBy { it.title.lowercase() })

    override suspend fun findById(id: RewardId): Reward? = stored[id]

    override suspend fun create(
        reward: Reward,
        by: MemberId,
        at: Instant,
    ) {
        stored[reward.id] = reward
    }

    override suspend fun update(
        reward: Reward,
        at: Instant,
    ): Boolean {
        if (stored[reward.id]?.archived != false) return false
        stored[reward.id] = reward
        return true
    }

    override suspend fun archive(
        id: RewardId,
        at: Instant,
    ): Boolean {
        val reward = stored[id] ?: return false
        stored[id] = reward.copy(archived = true)
        return true
    }

    override suspend fun goalOf(memberId: MemberId): RewardId? = goals[memberId]

    override suspend fun setGoal(
        memberId: MemberId,
        rewardId: RewardId,
        at: Instant,
    ) {
        goals[memberId] = rewardId
    }

    override suspend fun clearGoal(memberId: MemberId) {
        goals.remove(memberId)
    }
}

/** Books debit and refund in the shared [ledger] like the real repository does in one transaction. */
class InMemoryRedemptionRepository(
    private val ledger: InMemoryLedgerRepository,
    private val rewards: InMemoryRewardRepository,
) : RedemptionRepository {
    private val stored = mutableListOf<Redemption>()

    override suspend fun redeem(
        id: RedemptionId,
        memberId: MemberId,
        rewardId: RewardId,
        debitEntryId: String,
        at: Instant,
    ): RedeemOutcome {
        val reward = rewards.findById(rewardId) ?: return RedeemOutcome.RewardNotFound
        if (reward.archived) return RedeemOutcome.RewardArchived
        val balance = ledger.totalsOf(memberId).balance
        if (balance < reward.price) return RedeemOutcome.InsufficientBalance(balance, reward.price)
        val redemption =
            Redemption(id, memberId, rewardId, reward.title, reward.emoji, reward.price, RedemptionStatus.PENDING, null, at, null)
        stored += redemption
        val title = "Belohnung: ${reward.title}"
        val key = rewardSourceKey(id.value)
        ledger.append(LedgerEntry(LedgerEntryId(debitEntryId), memberId, EntryKind.REWARD, -reward.price, title, null, key, memberId, at))
        if (rewards.goalOf(memberId) == rewardId) rewards.clearGoal(memberId)
        return RedeemOutcome.Redeemed(redemption)
    }

    override suspend fun findById(id: RedemptionId): Redemption? = stored.firstOrNull { it.id == id }

    override suspend fun find(
        memberId: MemberId?,
        status: RedemptionStatus?,
        limit: Int,
    ): List<Redemption> =
        stored
            .withIndex()
            .filter { (memberId == null || it.value.memberId == memberId) && (status == null || it.value.status == status) }
            .sortedWith(compareByDescending<IndexedValue<Redemption>> { it.value.requestedAt }.thenByDescending { it.index })
            .take(limit)
            .map { it.value }

    override suspend fun fulfill(
        id: RedemptionId,
        by: MemberId,
        at: Instant,
    ): DecisionOutcome = decide(id, RedemptionStatus.FULFILLED, null, at)

    override suspend fun reject(
        id: RedemptionId,
        by: MemberId,
        reason: String,
        at: Instant,
        refundEntryId: String,
    ): DecisionOutcome {
        val outcome = decide(id, RedemptionStatus.REJECTED, reason, at)
        if (outcome == DecisionOutcome.CHANGED) {
            val r = checkNotNull(findById(id))
            val title = "Zurückgebucht: ${r.title}"
            val key = rewardRefundSourceKey(id.value)
            val kind = EntryKind.REWARD_REFUND
            ledger.append(LedgerEntry(LedgerEntryId(refundEntryId), r.memberId, kind, r.price, title, reason, key, by, at))
        }
        return outcome
    }

    private fun decide(
        id: RedemptionId,
        status: RedemptionStatus,
        reason: String?,
        at: Instant,
    ): DecisionOutcome {
        val index = stored.indexOfFirst { it.id == id }
        if (index < 0) return DecisionOutcome.NOT_FOUND
        if (stored[index].status != RedemptionStatus.PENDING) return DecisionOutcome.NOT_PENDING
        stored[index] = stored[index].copy(status = status, rejectionReason = reason, decidedAt = at)
        return DecisionOutcome.CHANGED
    }
}
