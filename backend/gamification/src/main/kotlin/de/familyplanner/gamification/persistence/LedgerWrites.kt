package de.familyplanner.gamification.persistence

import de.familyplanner.core.member.MemberId
import de.familyplanner.gamification.ledger.LedgerEntry
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Balance = sum of all ledger rows of the member (see `totalsFrom`). */
internal fun JdbcTransaction.balanceOf(memberId: MemberId): Int =
    LedgerTable.selectAll().where { LedgerTable.memberId eq memberId.value }.sumOf { it[LedgerTable.amount] }

/** Plain `insert` (never `insertIgnore`) so a failing ledger write rolls the surrounding transaction back. */
internal fun JdbcTransaction.insertEntry(entry: LedgerEntry) {
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
