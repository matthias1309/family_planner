package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.feature.gamification.domain.EntryKind
import de.familyplanner.android.feature.gamification.domain.LedgerEntry
import de.familyplanner.android.feature.gamification.domain.PointsAccount
import de.familyplanner.android.feature.gamification.domain.RankedChild
import de.familyplanner.api.model.LeaderboardEntry as ApiLeaderboardEntry
import de.familyplanner.api.model.LedgerEntry as ApiLedgerEntry
import de.familyplanner.api.model.PointsAccount as ApiPointsAccount

internal fun ApiLedgerEntry.toDomain(): LedgerEntry? {
    val parsedKind = EntryKind.entries.firstOrNull { it.name == kind.name } ?: return null
    val booked = parseInstant(bookedAt) ?: return null
    return LedgerEntry(id, parsedKind, amount, title, note, booked)
}

internal fun ApiPointsAccount.toDomain(): PointsAccount? {
    val entries = items.mapAllOrNull { it.toDomain() } ?: return null
    return PointsAccount(memberId, collected, balance, entries)
}

internal fun ApiLeaderboardEntry.toDomain() = RankedChild(memberId, name, rank, collected)
