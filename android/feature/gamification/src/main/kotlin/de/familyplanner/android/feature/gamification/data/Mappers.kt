package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.feature.gamification.domain.Badge
import de.familyplanner.android.feature.gamification.domain.BadgeOverview
import de.familyplanner.android.feature.gamification.domain.BadgeProgress
import de.familyplanner.android.feature.gamification.domain.EntryKind
import de.familyplanner.android.feature.gamification.domain.LedgerEntry
import de.familyplanner.android.feature.gamification.domain.PointsAccount
import de.familyplanner.android.feature.gamification.domain.RankedChild
import de.familyplanner.api.model.Badge as ApiBadge
import de.familyplanner.api.model.BadgeOverview as ApiBadgeOverview
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

internal fun ApiBadge.toDomain(): Badge? {
    val awarded = awardedAt?.let { parseInstant(it) ?: return null }
    if (earned && awarded == null) return null
    return Badge(id, emoji, title, description, hint, secret, earned, awarded, progress?.let { BadgeProgress(it.current, it.target) })
}

internal fun ApiBadgeOverview.toDomain(): BadgeOverview? {
    val badges = items.mapAllOrNull { it.toDomain() } ?: return null
    return BadgeOverview(memberId, earnedCount, totalCount, badges)
}
