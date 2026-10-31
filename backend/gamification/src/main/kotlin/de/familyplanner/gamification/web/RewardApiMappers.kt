package de.familyplanner.gamification.web

import de.familyplanner.api.model.BadgeProgress
import de.familyplanner.gamification.rewards.Redemption
import de.familyplanner.gamification.rewards.Reward
import de.familyplanner.gamification.rewards.RewardInput
import de.familyplanner.gamification.rewards.SavingsGoal
import de.familyplanner.api.model.Redemption as ApiRedemption
import de.familyplanner.api.model.RedemptionStatus as ApiRedemptionStatus
import de.familyplanner.api.model.Reward as ApiReward
import de.familyplanner.api.model.RewardInput as ApiRewardInput
import de.familyplanner.api.model.SavingsGoal as ApiSavingsGoal

fun Reward.toApi() = ApiReward(id = id.value, title = title, emoji = emoji, price = price, archived = archived)

fun ApiRewardInput.toInput() = RewardInput(title = title, emoji = emoji, price = price)

fun Redemption.toApi() =
    ApiRedemption(
        id = id.value,
        memberId = memberId.value,
        rewardId = rewardId.value,
        title = title,
        emoji = emoji,
        price = price,
        status = ApiRedemptionStatus.valueOf(status.name),
        rejectionReason = rejectionReason,
        requestedAt = requestedAt.toString(),
        decidedAt = decidedAt?.toString(),
    )

fun SavingsGoal.toApi() =
    ApiSavingsGoal(
        memberId = memberId.value,
        reward = reward?.toApi(),
        balance = balance,
        progress = reward?.let { BadgeProgress(current = balance.coerceIn(0, it.price), target = it.price) },
        redeemable = redeemable,
    )
