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
