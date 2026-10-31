package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.core.data.AppError
import java.time.Instant

enum class EntryKind { TASK, COOKING, MANUAL, SHOP, REWARD, REWARD_REFUND }

data class LedgerEntry(
    val id: String,
    val kind: EntryKind,
    val amount: Int,
    val title: String,
    val note: String?,
    val bookedAt: Instant,
)

data class PointsAccount(
    val memberId: String,
    val collected: Int,
    val balance: Int,
    val entries: List<LedgerEntry>,
)

data class RankedChild(
    val memberId: String,
    val name: String,
    val rank: Int,
    val collected: Int,
)

/** Stand vom Server oder aus dem lokalen Stand (ADR-0011); [account] und [badges] nur für Kinder (eigene). */
data class PointsSnapshot(
    val account: PointsAccount?,
    val leaderboard: List<RankedChild>,
    val syncedAt: Instant,
    val badges: BadgeOverview? = null,
)

sealed interface PointsLoad {
    /** [newBadges]: seit dem zuletzt gespeicherten Stand erhaltene Badges (REQ-GAME-020). */
    data class Fresh(
        val snapshot: PointsSnapshot,
        val newBadges: List<Badge> = emptyList(),
    ) : PointsLoad

    data class Cached(
        val snapshot: PointsSnapshot,
        val error: AppError,
    ) : PointsLoad

    data class Failed(
        val error: AppError,
    ) : PointsLoad
}

/** Platz des Kindes in der Rangliste; `null`, wenn es fehlt. */
fun List<RankedChild>.rankOf(memberId: String): RankedChild? = firstOrNull { it.memberId == memberId }
