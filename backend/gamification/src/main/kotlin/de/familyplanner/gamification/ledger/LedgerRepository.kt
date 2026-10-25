package de.familyplanner.gamification.ledger

import de.familyplanner.core.member.MemberId

interface LedgerRepository {
    /** Appends [entry]; returns false (and stores nothing) if an entry with the same non-null sourceKey exists. */
    suspend fun append(entry: LedgerEntry): Boolean

    suspend fun totalsOf(memberId: MemberId): Totals

    /** Totals of every member that has at least one entry. */
    suspend fun allTotals(): Map<MemberId, Totals>

    /** Newest first (bookedAt, then insertion order). */
    suspend fun entriesOf(
        memberId: MemberId,
        limit: Int,
    ): List<LedgerEntry>
}
