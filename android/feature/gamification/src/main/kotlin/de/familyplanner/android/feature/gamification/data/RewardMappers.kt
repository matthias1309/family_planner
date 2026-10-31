package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.feature.gamification.domain.BadgeProgress
import de.familyplanner.android.feature.gamification.domain.Redemption
import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import de.familyplanner.android.feature.gamification.domain.Reward
import de.familyplanner.android.feature.gamification.domain.SavingsGoal
import de.familyplanner.api.model.Redemption as ApiRedemption
import de.familyplanner.api.model.Reward as ApiReward
import de.familyplanner.api.model.SavingsGoal as ApiSavingsGoal

internal fun ApiReward.toDomain() = Reward(id, title, emoji, price, archived)

internal fun ApiRedemption.toDomain(): Redemption? {
    val requested = parseInstant(requestedAt)
    val decided = decidedAt?.let { parseInstant(it) }
    val parsedStatus = RedemptionStatus.entries.firstOrNull { it.name == status.name }
    // Ein vorhandenes, aber unlesbares Datum und eine entschiedene Einlösung ohne Datum sind Serverfehler.
    val decidedMissing = decidedAt != null && decided == null || parsedStatus != RedemptionStatus.PENDING && decided == null
    if (requested == null || parsedStatus == null || decidedMissing) return null
    return Redemption(id, memberId, rewardId, title, emoji, price, parsedStatus, rejectionReason, requested, decided)
}

internal fun ApiSavingsGoal.toDomain() =
    SavingsGoal(memberId, reward?.toDomain(), balance, progress?.let { BadgeProgress(it.current, it.target) }, redeemable)
