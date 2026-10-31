package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.feature.gamification.domain.Badge
import de.familyplanner.android.feature.gamification.domain.BadgeOverview
import de.familyplanner.android.feature.gamification.domain.BadgeProgress
import de.familyplanner.android.feature.gamification.domain.EntryKind
import de.familyplanner.android.feature.gamification.domain.LedgerEntry
import de.familyplanner.android.feature.gamification.domain.PointsAccount
import de.familyplanner.android.feature.gamification.domain.PointsSnapshot
import de.familyplanner.android.feature.gamification.domain.RankedChild
import de.familyplanner.android.feature.gamification.domain.Reward
import de.familyplanner.android.feature.gamification.domain.SavingsGoal
import kotlinx.serialization.Serializable
import java.time.Instant

/** 1: Punkte (Phase 1b); 2: Badges (Phase 2b); 3: Sparziel (Phase 3b). */
internal const val CACHE_VERSION = 3

/** Dateiformat des lokalen Punktestands (ADR-0011), getrennt von der Domäne; Zeitpunkte als Epoch-Millis. */
@Serializable
data class StoredPoints(
    val version: Int = CACHE_VERSION,
    val memberId: String? = null,
    val syncedAtMillis: Long = 0,
    val account: StoredAccount? = null,
    val leaderboard: List<StoredRankedChild> = emptyList(),
    val badges: StoredBadgeOverview? = null,
    val savingsGoal: StoredSavingsGoal? = null,
)

@Serializable
data class StoredAccount(
    val memberId: String,
    val collected: Int,
    val balance: Int,
    val entries: List<StoredEntry>,
)

@Serializable
data class StoredEntry(
    val id: String,
    val kind: String,
    val amount: Int,
    val title: String,
    val note: String? = null,
    val bookedAtMillis: Long,
)

@Serializable
data class StoredBadgeOverview(
    val memberId: String,
    val earnedCount: Int,
    val totalCount: Int,
    val items: List<StoredBadge>,
)

@Serializable
data class StoredBadge(
    val id: String,
    val emoji: String? = null,
    val title: String? = null,
    val description: String? = null,
    val hint: String? = null,
    val secret: Boolean,
    val earned: Boolean,
    val awardedAtMillis: Long? = null,
    val progressCurrent: Int? = null,
    val progressTarget: Int? = null,
)

@Serializable
data class StoredSavingsGoal(
    val memberId: String,
    val balance: Int,
    val redeemable: Boolean,
    val reward: StoredReward? = null,
    val progressCurrent: Int? = null,
    val progressTarget: Int? = null,
)

@Serializable
data class StoredReward(
    val id: String,
    val title: String,
    val emoji: String? = null,
    val price: Int,
    val archived: Boolean,
)

@Serializable
data class StoredRankedChild(
    val memberId: String,
    val name: String,
    val rank: Int,
    val collected: Int,
)

internal fun PointsSnapshot.toStored(memberId: String) =
    StoredPoints(
        memberId = memberId,
        syncedAtMillis = syncedAt.toEpochMilli(),
        account = account?.toStored(),
        leaderboard = leaderboard.map { StoredRankedChild(it.memberId, it.name, it.rank, it.collected) },
        badges = badges?.toStored(),
        savingsGoal = savingsGoal?.toStored(),
    )

private fun SavingsGoal.toStored() =
    StoredSavingsGoal(
        memberId,
        balance,
        redeemable,
        reward?.let { StoredReward(it.id, it.title, it.emoji, it.price, it.archived) },
        progress?.current,
        progress?.target,
    )

private fun StoredSavingsGoal.toDomain(): SavingsGoal {
    val current = progressCurrent
    val target = progressTarget
    val progress = if (current != null && target != null) BadgeProgress(current, target) else null
    return SavingsGoal(
        memberId,
        reward?.let { Reward(it.id, it.title, it.emoji, it.price, it.archived) },
        balance,
        progress,
        redeemable,
    )
}

private fun PointsAccount.toStored() =
    StoredAccount(
        memberId,
        collected,
        balance,
        entries.map { StoredEntry(it.id, it.kind.name, it.amount, it.title, it.note, it.bookedAt.toEpochMilli()) },
    )

private fun BadgeOverview.toStored() =
    StoredBadgeOverview(
        memberId,
        earnedCount,
        totalCount,
        items.map {
            StoredBadge(
                id = it.id,
                emoji = it.emoji,
                title = it.title,
                description = it.description,
                hint = it.hint,
                secret = it.secret,
                earned = it.earned,
                awardedAtMillis = it.awardedAt?.toEpochMilli(),
                progressCurrent = it.progress?.current,
                progressTarget = it.progress?.target,
            )
        },
    )

/** Besitzer und Stand; `null` ohne Besitzer oder mit unlesbarer Art — der Stand zählt dann als nicht gespeichert. */
internal fun StoredPoints.toDomain(): Pair<String, PointsSnapshot>? {
    val owner = memberId ?: return null
    val storedAccount = account
    val mappedAccount = storedAccount?.let { it.toDomain() ?: return null }
    val ranked = leaderboard.map { RankedChild(it.memberId, it.name, it.rank, it.collected) }
    val mappedBadges = badges?.let { it.toDomain() ?: return null }
    return owner to PointsSnapshot(mappedAccount, ranked, Instant.ofEpochMilli(syncedAtMillis), mappedBadges, savingsGoal?.toDomain())
}

private fun StoredAccount.toDomain(): PointsAccount? {
    val mapped =
        entries.mapAllOrNull { entry ->
            val kind = EntryKind.entries.firstOrNull { it.name == entry.kind } ?: return@mapAllOrNull null
            LedgerEntry(entry.id, kind, entry.amount, entry.title, entry.note, Instant.ofEpochMilli(entry.bookedAtMillis))
        } ?: return null
    return PointsAccount(memberId, collected, balance, mapped)
}

private fun StoredBadgeOverview.toDomain(): BadgeOverview? {
    val mapped =
        items.mapAllOrNull { badge ->
            val current = badge.progressCurrent
            val target = badge.progressTarget
            val progress = if (current != null && target != null) BadgeProgress(current, target) else null
            val awardedAt = badge.awardedAtMillis?.let { Instant.ofEpochMilli(it) }
            if (badge.earned && awardedAt == null) return@mapAllOrNull null
            Badge(
                badge.id,
                badge.emoji,
                badge.title,
                badge.description,
                badge.hint,
                badge.secret,
                badge.earned,
                awardedAt,
                progress,
            )
        } ?: return null
    return BadgeOverview(memberId, earnedCount, totalCount, mapped)
}
