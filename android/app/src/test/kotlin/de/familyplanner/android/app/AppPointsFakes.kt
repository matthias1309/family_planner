package de.familyplanner.android.app

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.feature.auth.domain.FamilyDevice
import de.familyplanner.android.feature.auth.domain.FamilyMember
import de.familyplanner.android.feature.auth.domain.FamilyRepository
import de.familyplanner.android.feature.auth.domain.IssuedPairingCode
import de.familyplanner.android.feature.gamification.domain.BadgeOverview
import de.familyplanner.android.feature.gamification.domain.EntryKind
import de.familyplanner.android.feature.gamification.domain.LedgerEntry
import de.familyplanner.android.feature.gamification.domain.PointsAccount
import de.familyplanner.android.feature.gamification.domain.PointsCache
import de.familyplanner.android.feature.gamification.domain.PointsRepository
import de.familyplanner.android.feature.gamification.domain.PointsSnapshot
import de.familyplanner.android.feature.gamification.domain.RankedChild
import de.familyplanner.android.feature.gamification.domain.Redemption
import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import de.familyplanner.android.feature.gamification.domain.Reward
import de.familyplanner.android.feature.gamification.domain.RewardRepository
import de.familyplanner.android.feature.gamification.domain.SavingsGoal
import java.time.Instant

private val bookedAt = Instant.parse("2026-10-25T10:00:00Z")

/** Punktekonto mit fester Antwort; Buchen und Einstellen sind im Navigationstest nicht gefragt. */
class StaticPointsRepository : PointsRepository {
    private val ranking = listOf(RankedChild("c-2", "Ben", 1, 57), RankedChild("m", "Lea", 2, 42))

    override suspend fun leaderboard(): ApiResult<List<RankedChild>> = ApiResult.Success(ranking)

    override suspend fun account(
        memberId: String,
        limit: Int,
    ): ApiResult<PointsAccount> =
        ApiResult.Success(PointsAccount(memberId, 42, 40, listOf(LedgerEntry("e-1", EntryKind.TASK, 3, "Bad putzen", null, bookedAt))))

    override suspend fun badges(memberId: String): ApiResult<BadgeOverview> = ApiResult.Success(BadgeOverview(memberId, 0, 12, emptyList()))

    override suspend fun book(
        memberId: String,
        amount: Int,
        reason: String,
    ): ApiResult<LedgerEntry> = error("not used")

    override suspend fun cookingPoints(): ApiResult<Int> = ApiResult.Success(3)

    override suspend fun updateCookingPoints(points: Int): ApiResult<Int> = ApiResult.Success(points)
}

class MemoryPointsCache : PointsCache {
    private var owner: String? = null
    private var stored: PointsSnapshot? = null

    override suspend fun read(memberId: String): PointsSnapshot? = stored.takeIf { owner == memberId }

    override suspend fun write(
        memberId: String,
        snapshot: PointsSnapshot,
    ) {
        owner = memberId
        stored = snapshot
    }

    override suspend fun clear() {
        owner = null
        stored = null
    }
}

/** Familie ohne Mitglieder und Geräte, damit Namen nur in der Rangliste vorkommen. */
class EmptyFamilyRepository : FamilyRepository {
    override suspend fun members(): ApiResult<List<FamilyMember>> = ApiResult.Success(emptyList())

    override suspend fun createMember(
        name: String,
        role: Role,
    ): ApiResult<FamilyMember> = error("not used")

    override suspend fun createPairingCode(memberId: String): ApiResult<IssuedPairingCode> = error("not used")

    override suspend fun devices(): ApiResult<List<FamilyDevice>> = ApiResult.Success(emptyList())

    override suspend fun revokeDevice(deviceId: String): ApiResult<Unit> = error("not used")
}

/** Belohnungen ohne Inhalt und ohne Sparziel; Aktionen sind im Navigationstest nicht gefragt. */
class StaticRewardRepository : RewardRepository {
    override suspend fun rewards(): ApiResult<List<Reward>> = ApiResult.Success(emptyList())

    override suspend fun create(
        title: String,
        emoji: String?,
        price: Int,
    ): ApiResult<Reward> = error("not used")

    override suspend fun update(
        rewardId: String,
        title: String,
        emoji: String?,
        price: Int,
    ): ApiResult<Reward> = error("not used")

    override suspend fun archive(rewardId: String): ApiResult<Unit> = error("not used")

    override suspend fun redeem(rewardId: String): ApiResult<Redemption> = error("not used")

    override suspend fun redemptions(
        status: RedemptionStatus?,
        limit: Int,
    ): ApiResult<List<Redemption>> = ApiResult.Success(emptyList())

    override suspend fun fulfill(redemptionId: String): ApiResult<Redemption> = error("not used")

    override suspend fun reject(
        redemptionId: String,
        reason: String,
    ): ApiResult<Redemption> = error("not used")

    override suspend fun savingsGoal(memberId: String): ApiResult<SavingsGoal> =
        ApiResult.Success(SavingsGoal(memberId, null, 40, null, false))

    override suspend fun setSavingsGoal(
        memberId: String,
        rewardId: String,
    ): ApiResult<SavingsGoal> = error("not used")

    override suspend fun clearSavingsGoal(memberId: String): ApiResult<Unit> = error("not used")
}
