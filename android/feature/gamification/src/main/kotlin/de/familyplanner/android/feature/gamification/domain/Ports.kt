package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.feature.gamification.domain.notice.RedemptionSnapshot
import de.familyplanner.android.feature.gamification.domain.notice.RewardNotice

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

// Spiegelt die elf Endpunkte für Belohnungen, Einlösungen und Sparziel; Aufteilen würde nur Adapter und Fakes verdoppeln.
@Suppress("TooManyFunctions")
interface RewardRepository {
    /** Aktive Belohnungen, nach Preis. */
    suspend fun rewards(): ApiResult<List<Reward>>

    suspend fun create(
        title: String,
        emoji: String?,
        price: Int,
    ): ApiResult<Reward>

    suspend fun update(
        rewardId: String,
        title: String,
        emoji: String?,
        price: Int,
    ): ApiResult<Reward>

    suspend fun archive(rewardId: String): ApiResult<Unit>

    suspend fun redeem(rewardId: String): ApiResult<Redemption>

    suspend fun redemptions(
        status: RedemptionStatus?,
        limit: Int,
    ): ApiResult<List<Redemption>>

    suspend fun fulfill(redemptionId: String): ApiResult<Redemption>

    suspend fun reject(
        redemptionId: String,
        reason: String,
    ): ApiResult<Redemption>

    suspend fun savingsGoal(memberId: String): ApiResult<SavingsGoal>

    suspend fun setSavingsGoal(
        memberId: String,
        rewardId: String,
    ): ApiResult<SavingsGoal>

    suspend fun clearSavingsGoal(memberId: String): ApiResult<Unit>
}

/** Zuletzt abgeglichener Stand der Einlösungen je Mitglied (ADR-0013); Fehler beim Speichern sind nie ein Nutzerfehler. */
interface RedemptionNoticeStore {
    suspend fun read(memberId: String): RedemptionSnapshot?

    suspend fun write(
        memberId: String,
        snapshot: RedemptionSnapshot,
    )

    suspend fun clear()
}

interface RewardNotifier {
    fun announce(notice: RewardNotice)
}

/** Stößt den Abgleich im Hintergrund an (WorkManager, ADR-0013). */
interface RewardSync {
    fun requestNow()

    fun schedulePeriodic()
}
