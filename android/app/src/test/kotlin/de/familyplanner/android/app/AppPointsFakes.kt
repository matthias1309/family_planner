package de.familyplanner.android.app

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.feature.auth.domain.FamilyDevice
import de.familyplanner.android.feature.auth.domain.FamilyMember
import de.familyplanner.android.feature.auth.domain.FamilyRepository
import de.familyplanner.android.feature.auth.domain.IssuedPairingCode
import de.familyplanner.android.feature.gamification.domain.EntryKind
import de.familyplanner.android.feature.gamification.domain.LedgerEntry
import de.familyplanner.android.feature.gamification.domain.PointsAccount
import de.familyplanner.android.feature.gamification.domain.PointsCache
import de.familyplanner.android.feature.gamification.domain.PointsRepository
import de.familyplanner.android.feature.gamification.domain.PointsSnapshot
import de.familyplanner.android.feature.gamification.domain.RankedChild
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
