package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.core.data.ApiResult

interface PointsRepository {
    suspend fun leaderboard(): ApiResult<List<RankedChild>>

    suspend fun account(
        memberId: String,
        limit: Int,
    ): ApiResult<PointsAccount>

    suspend fun badges(memberId: String): ApiResult<BadgeOverview>

    suspend fun book(
        memberId: String,
        amount: Int,
        reason: String,
    ): ApiResult<LedgerEntry>

    suspend fun cookingPoints(): ApiResult<Int>

    suspend fun updateCookingPoints(points: Int): ApiResult<Int>
}

/** Lokaler Lese-Stand (ADR-0011). Fehler beim Speichern sind nie ein Nutzerfehler. */
interface PointsCache {
    /** Gespeicherter Stand für dieses Mitglied, sonst `null`. */
    suspend fun read(memberId: String): PointsSnapshot?

    suspend fun write(
        memberId: String,
        snapshot: PointsSnapshot,
    )

    suspend fun clear()
}
