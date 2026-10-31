package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.core.data.apiCallNoContent
import de.familyplanner.android.feature.gamification.domain.Redemption
import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import de.familyplanner.android.feature.gamification.domain.Reward
import de.familyplanner.android.feature.gamification.domain.RewardRepository
import de.familyplanner.android.feature.gamification.domain.SavingsGoal
import de.familyplanner.api.client.GamificationApi
import de.familyplanner.api.model.RejectRedemptionRequest
import de.familyplanner.api.model.RewardInput
import de.familyplanner.api.model.SavingsGoalInput
import kotlinx.serialization.json.Json
import de.familyplanner.api.model.RedemptionStatus as ApiRedemptionStatus

class RemoteRewardRepository(
    private val api: GamificationApi,
    private val json: Json,
) : RewardRepository {
    override suspend fun rewards(): ApiResult<List<Reward>> =
        apiCall(json, { api.listRewards(false) }) { response -> response.data?.map { it.toDomain() } }

    override suspend fun create(
        title: String,
        emoji: String?,
        price: Int,
    ): ApiResult<Reward> =
        apiCall(json, { api.createReward(RewardInput(title = title, price = price, emoji = emoji)) }) { it.data?.toDomain() }

    override suspend fun update(
        rewardId: String,
        title: String,
        emoji: String?,
        price: Int,
    ): ApiResult<Reward> =
        apiCall(json, { api.updateReward(rewardId, RewardInput(title = title, price = price, emoji = emoji)) }) { it.data?.toDomain() }

    override suspend fun archive(rewardId: String): ApiResult<Unit> = apiCallNoContent(json) { api.archiveReward(rewardId) }

    override suspend fun redeem(rewardId: String): ApiResult<Redemption> =
        apiCall(json, { api.redeemReward(rewardId) }) { it.data?.toDomain() }

    override suspend fun redemptions(
        status: RedemptionStatus?,
        limit: Int,
    ): ApiResult<List<Redemption>> =
        apiCall(json, { api.listRedemptions(status?.let { ApiRedemptionStatus.valueOf(it.name) }, limit) }) { response ->
            response.data?.mapAllOrNull { it.toDomain() }
        }

    override suspend fun fulfill(redemptionId: String): ApiResult<Redemption> =
        apiCall(json, { api.fulfillRedemption(redemptionId) }) { it.data?.toDomain() }

    override suspend fun reject(
        redemptionId: String,
        reason: String,
    ): ApiResult<Redemption> =
        apiCall(json, { api.rejectRedemption(redemptionId, RejectRedemptionRequest(reason)) }) { it.data?.toDomain() }

    override suspend fun savingsGoal(memberId: String): ApiResult<SavingsGoal> =
        apiCall(json, { api.getSavingsGoal(memberId) }) { it.data?.toDomain() }

    override suspend fun setSavingsGoal(
        memberId: String,
        rewardId: String,
    ): ApiResult<SavingsGoal> = apiCall(json, { api.setSavingsGoal(memberId, SavingsGoalInput(rewardId)) }) { it.data?.toDomain() }

    override suspend fun clearSavingsGoal(memberId: String): ApiResult<Unit> = apiCallNoContent(json) { api.clearSavingsGoal(memberId) }
}
