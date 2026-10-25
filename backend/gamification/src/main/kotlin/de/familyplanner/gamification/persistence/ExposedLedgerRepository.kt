package de.familyplanner.gamification.persistence

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.gamification.ledger.EntryKind
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.LedgerEntryId
import de.familyplanner.gamification.ledger.LedgerRepository
import de.familyplanner.gamification.ledger.Totals
import de.familyplanner.gamification.ledger.totalsFrom
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant

class ExposedLedgerRepository(
    private val db: Database,
) : LedgerRepository {
    /** `INSERT OR IGNORE` also hides CHECK violations, so callers validate the amount first. */
    override suspend fun append(entry: LedgerEntry): Boolean =
        dbQuery(db) {
            val statement =
                LedgerTable.insertIgnore {
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
            statement.insertedCount > 0
        }

    override suspend fun totalsOf(memberId: MemberId): Totals =
        dbQuery(db) {
            totalsFrom(
                LedgerTable
                    .selectAll()
                    .where { LedgerTable.memberId eq memberId.value }
                    .map { EntryKind.valueOf(it[LedgerTable.kind]) to it[LedgerTable.amount] },
            )
        }

    override suspend fun allTotals(): Map<MemberId, Totals> =
        dbQuery(db) {
            LedgerTable
                .selectAll()
                .groupBy({ MemberId(it[LedgerTable.memberId]) }, { EntryKind.valueOf(it[LedgerTable.kind]) to it[LedgerTable.amount] })
                .mapValues { totalsFrom(it.value) }
        }

    override suspend fun entriesOf(
        memberId: MemberId,
        limit: Int,
    ): List<LedgerEntry> =
        dbQuery(db) {
            LedgerTable
                .selectAll()
                .where { LedgerTable.memberId eq memberId.value }
                .orderBy(LedgerTable.bookedAt to SortOrder.DESC, LedgerTable.seq to SortOrder.DESC)
                .limit(limit)
                .map { it.toEntry() }
        }
}

private fun ResultRow.toEntry() =
    LedgerEntry(
        id = LedgerEntryId(this[LedgerTable.id]),
        memberId = MemberId(this[LedgerTable.memberId]),
        kind = EntryKind.valueOf(this[LedgerTable.kind]),
        amount = this[LedgerTable.amount],
        title = this[LedgerTable.title],
        note = this[LedgerTable.note],
        sourceKey = this[LedgerTable.sourceKey],
        createdBy = this[LedgerTable.createdBy]?.let(::MemberId),
        bookedAt = Instant.ofEpochMilli(this[LedgerTable.bookedAt]),
    )
