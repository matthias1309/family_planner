package de.familyplanner.android.feature.gamification.fakes

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.feature.gamification.domain.BadgeProgress
import de.familyplanner.android.feature.gamification.domain.Redemption
import de.familyplanner.android.feature.gamification.domain.RedemptionNoticeStore
import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import de.familyplanner.android.feature.gamification.domain.Reward
import de.familyplanner.android.feature.gamification.domain.RewardNotifier
import de.familyplanner.android.feature.gamification.domain.RewardRepository
import de.familyplanner.android.feature.gamification.domain.RewardSync
import de.familyplanner.android.feature.gamification.domain.SavingsGoal
import de.familyplanner.android.feature.gamification.domain.notice.RedemptionSnapshot
import de.familyplanner.android.feature.gamification.domain.notice.RewardNotice
import java.time.Instant

fun reward(
    id: String,
    price: Int = 200,
    title: String = "Freizeitpark",
    emoji: String? = "🎢",
    archived: Boolean = false,
) = Reward(id, title, emoji, price, archived)

fun redemption(
    id: String,
    status: RedemptionStatus = RedemptionStatus.PENDING,
    memberId: String = "c-1",
    decidedAt: Instant? = if (status == RedemptionStatus.PENDING) null else noon,
    reason: String? = null,
    title: String = "Pizzaabend",
    price: Int = 80,
) = Redemption(id, memberId, "r-2", title, "🍕", price, status, reason, noon, decidedAt)

fun goal(
    reward: Reward? = reward("r-1"),
    balance: Int = 120,
) = SavingsGoal(
    memberId = "c-1",
    reward = reward,
    balance = balance,
    progress = reward?.let { BadgeProgress(minOf(balance, it.price), it.price) },
    redeemable = reward != null && balance >= reward.price,
)

class FakeRewardRepository : RewardRepository {
    var rewardsResult: ApiResult<List<Reward>> = ApiResult.Success(listOf(reward("r-1"), reward("r-2", 80, "Pizzaabend", "🍕")))
    var createResult: ApiResult<Reward> = ApiResult.Success(reward("r-new"))
    var updateResult: ApiResult<Reward> = ApiResult.Success(reward("r-1"))
    var archiveResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var redeemResult: ApiResult<Redemption> = ApiResult.Success(redemption("d-new"))
    var redemptionsResult: ApiResult<List<Redemption>> = ApiResult.Success(emptyList())
    var fulfillResult: ApiResult<Redemption> = ApiResult.Success(redemption("d-1", RedemptionStatus.FULFILLED))
    var rejectResult: ApiResult<Redemption> = ApiResult.Success(redemption("d-1", RedemptionStatus.REJECTED, reason = "Ferien"))
    var goalResult: ApiResult<SavingsGoal> = ApiResult.Success(goal(reward = null))
    var setGoalResult: ApiResult<SavingsGoal> = ApiResult.Success(goal())
    var clearGoalResult: ApiResult<Unit> = ApiResult.Success(Unit)

    val createCalls = mutableListOf<Triple<String, String?, Int>>()
    val updateCalls = mutableListOf<List<Any?>>()
    val archiveCalls = mutableListOf<String>()
    val redeemCalls = mutableListOf<String>()
    val redemptionsCalls = mutableListOf<Pair<RedemptionStatus?, Int>>()
    val fulfillCalls = mutableListOf<String>()
    val rejectCalls = mutableListOf<Pair<String, String>>()
    val goalCalls = mutableListOf<String>()
    val setGoalCalls = mutableListOf<Pair<String, String>>()
    val clearGoalCalls = mutableListOf<String>()

    override suspend fun rewards() = rewardsResult

    override suspend fun create(
        title: String,
        emoji: String?,
        price: Int,
    ): ApiResult<Reward> {
        createCalls += Triple(title, emoji, price)
        return createResult
    }

    override suspend fun update(
        rewardId: String,
        title: String,
        emoji: String?,
        price: Int,
    ): ApiResult<Reward> {
        updateCalls += listOf(rewardId, title, emoji, price)
        return updateResult
    }

    override suspend fun archive(rewardId: String): ApiResult<Unit> {
        archiveCalls += rewardId
        return archiveResult
    }

    override suspend fun redeem(rewardId: String): ApiResult<Redemption> {
        redeemCalls += rewardId
        return redeemResult
    }

    override suspend fun redemptions(
        status: RedemptionStatus?,
        limit: Int,
    ): ApiResult<List<Redemption>> {
        redemptionsCalls += status to limit
        return redemptionsResult
    }

    override suspend fun fulfill(redemptionId: String): ApiResult<Redemption> {
        fulfillCalls += redemptionId
        return fulfillResult
    }

    override suspend fun reject(
        redemptionId: String,
        reason: String,
    ): ApiResult<Redemption> {
        rejectCalls += redemptionId to reason
        return rejectResult
    }

    override suspend fun savingsGoal(memberId: String): ApiResult<SavingsGoal> {
        goalCalls += memberId
        return goalResult
    }

    override suspend fun setSavingsGoal(
        memberId: String,
        rewardId: String,
    ): ApiResult<SavingsGoal> {
        setGoalCalls += memberId to rewardId
        return setGoalResult
    }

    override suspend fun clearSavingsGoal(memberId: String): ApiResult<Unit> {
        clearGoalCalls += memberId
        return clearGoalResult
    }
}

class FakeRewardSync : RewardSync {
    var nowCalls = 0
    var periodicCalls = 0

    override fun requestNow() {
        nowCalls++
    }

    override fun schedulePeriodic() {
        periodicCalls++
    }
}

class FakeRewardNotifier : RewardNotifier {
    val notices = mutableListOf<RewardNotice>()

    override fun announce(notice: RewardNotice) {
        notices += notice
    }
}

class InMemoryRedemptionNoticeStore : RedemptionNoticeStore {
    var owner: String? = null
    var stored: RedemptionSnapshot? = null
    var cleared = 0

    override suspend fun read(memberId: String): RedemptionSnapshot? = stored.takeIf { owner == memberId }

    override suspend fun write(
        memberId: String,
        snapshot: RedemptionSnapshot,
    ) {
        owner = memberId
        stored = snapshot
    }

    override suspend fun clear() {
        cleared++
        owner = null
        stored = null
    }
}
