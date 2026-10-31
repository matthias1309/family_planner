package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.map
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.data.validationFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Seite „Belohnungen“ (Kind): aktive Belohnungen, eigenes Sparziel, eigene Einlösungen — nur online. */
data class ChildRewards(
    val rewards: List<Reward>,
    val goal: SavingsGoal,
    val redemptions: List<Redemption>,
)

class LoadChildRewardsUseCase(
    private val repository: RewardRepository,
) {
    suspend operator fun invoke(memberId: String): ApiResult<ChildRewards> =
        coroutineScope {
            val rewards = async { repository.rewards() }
            val goal = async { repository.savingsGoal(memberId) }
            val redemptions = async { repository.redemptions(null, RewardLimits.CHILD_HISTORY_LIMIT) }
            val rewardsResult = rewards.await()
            val goalResult = goal.await()
            val redemptionsResult = redemptions.await()
            val failure = listOf(rewardsResult, goalResult, redemptionsResult).filterIsInstance<ApiResult.Failure>().firstOrNull()
            if (failure != null) return@coroutineScope failure
            ApiResult.Success(
                ChildRewards(
                    rewards = (rewardsResult as ApiResult.Success).data,
                    goal = (goalResult as ApiResult.Success).data,
                    redemptions = (redemptionsResult as ApiResult.Success).data,
                ),
            )
        }
}

class ChildRewardActions(
    private val repository: RewardRepository,
    private val sync: RewardSync,
) {
    suspend fun setGoal(
        memberId: String,
        rewardId: String,
    ) = repository.setSavingsGoal(memberId, rewardId)

    suspend fun clearGoal(memberId: String) = repository.clearSavingsGoal(memberId)

    /** Nach Erfolg sofort abgleichen, damit spätere Entscheidungen gemeldet werden (REQ-GAME-043). */
    suspend fun redeem(rewardId: String): ApiResult<Redemption> =
        repository.redeem(rewardId).also { if (it is ApiResult.Success) sync.requestNow() }
}

/** Seite „Belohnungen verwalten“ (Eltern). [names]: memberId → Name aus der Rangliste. */
data class ManagedRewards(
    val rewards: List<Reward>,
    val pending: List<Redemption>,
    val names: Map<String, String>,
)

class LoadManagedRewardsUseCase(
    private val repository: RewardRepository,
    private val points: PointsRepository,
) {
    suspend operator fun invoke(): ApiResult<ManagedRewards> =
        coroutineScope {
            val rewards = async { repository.rewards() }
            val pending = async { repository.redemptions(RedemptionStatus.PENDING, RewardLimits.PENDING_LIMIT) }
            val leaderboard = async { points.leaderboard() }
            val rewardsResult = rewards.await()
            val pendingResult = pending.await()
            val leaderboardResult = leaderboard.await()
            val failure = listOf(rewardsResult, pendingResult).filterIsInstance<ApiResult.Failure>().firstOrNull()
            if (failure != null) return@coroutineScope failure
            // Ohne Rangliste fehlen nur die Namen (Fallback im UI).
            val names = (leaderboardResult as? ApiResult.Success)?.data.orEmpty().associate { it.memberId to it.name }
            ApiResult.Success(
                ManagedRewards(
                    rewards = (rewardsResult as ApiResult.Success).data,
                    pending = (pendingResult as ApiResult.Success).data,
                    names = names,
                ),
            )
        }
}

class ManageRewardActions(
    private val repository: RewardRepository,
) {
    suspend fun save(draft: RewardDraft): ApiResult<Reward> {
        val price = draft.price ?: return validationFailure("Preis fehlt")
        validateReward(draft.title, draft.emoji, price)?.let { return ApiResult.Failure(it) }
        val title = draft.title.trim()
        val emoji = draft.emoji.trim().ifEmpty { null }
        return draft.rewardId?.let { repository.update(it, title, emoji, price) } ?: repository.create(title, emoji, price)
    }

    suspend fun archive(rewardId: String) = repository.archive(rewardId)

    suspend fun fulfill(redemptionId: String) = repository.fulfill(redemptionId)

    suspend fun reject(
        redemptionId: String,
        reason: String,
    ): ApiResult<Redemption> {
        validateRejection(reason)?.let { return ApiResult.Failure(it) }
        return repository.reject(redemptionId, reason.trim())
    }
}

/** Anzahl wartender Einlösungen für „Familie“. */
class CountPendingRedemptionsUseCase(
    private val repository: RewardRepository,
) {
    suspend operator fun invoke(): ApiResult<Int> =
        repository.redemptions(RedemptionStatus.PENDING, RewardLimits.PENDING_LIMIT).map { it.size }
}

/** Sparziel für „Heute“: frisch vom Server, sonst aus dem lokalen Punkte-Stand; nur Kinder. */
class LoadTodaySavingsUseCase(
    private val repository: RewardRepository,
    private val cache: PointsCache,
    private val sessions: SessionStore,
) {
    suspend operator fun invoke(): SavingsGoal? {
        val session = sessions.current?.takeIf { it.role == Role.CHILD } ?: return null
        return when (val result = repository.savingsGoal(session.memberId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> cache.read(session.memberId)?.savingsGoal
        }
    }
}
