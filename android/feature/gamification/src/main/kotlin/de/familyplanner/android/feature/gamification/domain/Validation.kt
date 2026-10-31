package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.core.data.AppError
import kotlin.math.abs

/** [amount] mit Vorzeichen; Server: −1000…1000, ≠ 0 (REQ-GAME-013). `null` = gültig. */
fun validateManualEntry(
    amount: Int,
    reason: String,
): AppError.Validation? =
    when {
        amount == 0 || abs(amount) > PointsLimits.AMOUNT_MAX ->
            AppError.Validation("Betrag muss zwischen 1 und ${PointsLimits.AMOUNT_MAX} liegen")
        reason.trim().length !in 1..PointsLimits.REASON_MAX ->
            AppError.Validation("Begründung muss zwischen 1 und ${PointsLimits.REASON_MAX} Zeichen lang sein")
        else -> null
    }

fun validateCookingPoints(points: Int): AppError.Validation? =
    if (points in PointsLimits.COOKING_POINTS_MIN..PointsLimits.COOKING_POINTS_MAX) {
        null
    } else {
        AppError.Validation("Kochpunkte müssen zwischen ${PointsLimits.COOKING_POINTS_MIN} und ${PointsLimits.COOKING_POINTS_MAX} liegen")
    }

/** `null` = gültig (REQ-GAME-040). */
fun validateReward(
    title: String,
    emoji: String,
    price: Int,
): AppError.Validation? =
    when {
        title.trim().length !in 1..RewardLimits.TITLE_MAX ->
            AppError.Validation("Titel muss zwischen 1 und ${RewardLimits.TITLE_MAX} Zeichen lang sein")
        emoji.trim().length > RewardLimits.EMOJI_MAX ->
            AppError.Validation("Symbol darf höchstens ${RewardLimits.EMOJI_MAX} Zeichen lang sein")
        price !in RewardLimits.PRICE_MIN..RewardLimits.PRICE_MAX ->
            AppError.Validation("Preis muss zwischen ${RewardLimits.PRICE_MIN} und ${RewardLimits.PRICE_MAX} liegen")
        else -> null
    }

/** `null` = gültig (REQ-GAME-043). */
fun validateRejection(reason: String): AppError.Validation? =
    if (reason.trim().length in 1..RewardLimits.REASON_MAX) {
        null
    } else {
        AppError.Validation("Begründung muss zwischen 1 und ${RewardLimits.REASON_MAX} Zeichen lang sein")
    }
