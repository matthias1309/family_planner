package de.familyplanner.gamification.fakes

import de.familyplanner.core.member.MemberId
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.LedgerRepository
import de.familyplanner.gamification.ledger.Totals
import de.familyplanner.gamification.ledger.totalsFrom
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
