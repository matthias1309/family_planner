package de.familyplanner.gamification.ledger

import de.familyplanner.core.member.MemberId
import java.time.Instant

@JvmInline
value class LedgerEntryId(
    val value: String,
)

/** [countsAsCollected] kinds raise "collected"; spending kinds only change the balance. */
enum class EntryKind(
    val countsAsCollected: Boolean,
) {
    TASK(true),
    COOKING(true),
    MANUAL(true),
    SHOP(false),
    REWARD(false),
    REWARD_REFUND(false),
}

/** One immutable booking; [sourceKey] is set for credits from events and makes them idempotent. */
data class LedgerEntry(
    val id: LedgerEntryId,
    val memberId: MemberId,
    val kind: EntryKind,
    val amount: Int,
    val title: String,
    val note: String?,
    val sourceKey: String?,
    val createdBy: MemberId?,
    val bookedAt: Instant,
)

/** Collected only grows with earned points (and manual corrections); balance is the sum of everything. */
data class Totals(
    val collected: Int,
    val balance: Int,
) {
    companion object {
        val ZERO = Totals(0, 0)
    }
}

const val MANUAL_AMOUNT_MAX = 1000
const val REASON_MAX = 200
const val ENTRIES_LIMIT_DEFAULT = 50
const val ENTRIES_LIMIT_MAX = 100

fun taskSourceKey(cardId: String) = "task:$cardId"

fun cookingSourceKey(dishId: String) = "cooking:$dishId"

fun rewardSourceKey(redemptionId: String) = "reward:$redemptionId"

fun rewardRefundSourceKey(redemptionId: String) = "reward-refund:$redemptionId"

/** Sums (kind, amount) rows into [Totals]. */
fun totalsFrom(rows: Iterable<Pair<EntryKind, Int>>): Totals =
    Totals(
        collected = rows.filter { it.first.countsAsCollected }.sumOf { it.second },
        balance = rows.sumOf { it.second },
    )
