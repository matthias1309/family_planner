package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.feature.gamification.domain.BadgeOverview
import de.familyplanner.android.feature.gamification.domain.LedgerEntry
import de.familyplanner.android.feature.gamification.domain.PointsAccount
import de.familyplanner.android.feature.gamification.domain.PointsRepository
import de.familyplanner.android.feature.gamification.domain.RankedChild
import de.familyplanner.api.client.GamificationApi
import de.familyplanner.api.model.GameSettings
import de.familyplanner.api.model.ManualEntryRequest
import kotlinx.serialization.json.Json

class RemotePointsRepository(
    private val api: GamificationApi,
    private val json: Json,
) : PointsRepository {
    override suspend fun leaderboard(): ApiResult<List<RankedChild>> =
        apiCall(json, { api.getLeaderboard() }) { response -> response.data?.items?.map { it.toDomain() } }

    override suspend fun account(
        memberId: String,
        limit: Int,
    ): ApiResult<PointsAccount> = apiCall(json, { api.getPointsAccount(memberId, limit) }) { it.data?.toDomain() }

    override suspend fun badges(memberId: String): ApiResult<BadgeOverview> =
        apiCall(json, { api.getBadges(memberId) }) { it.data?.toDomain() }

    override suspend fun book(
        memberId: String,
        amount: Int,
        reason: String,
    ): ApiResult<LedgerEntry> = apiCall(json, { api.bookPoints(memberId, ManualEntryRequest(amount, reason)) }) { it.data?.toDomain() }

    override suspend fun cookingPoints(): ApiResult<Int> = apiCall(json, { api.getGameSettings() }) { it.data?.cookingPoints }

    override suspend fun updateCookingPoints(points: Int): ApiResult<Int> =
        apiCall(json, { api.updateGameSettings(GameSettings(points)) }) { it.data?.cookingPoints }
}
