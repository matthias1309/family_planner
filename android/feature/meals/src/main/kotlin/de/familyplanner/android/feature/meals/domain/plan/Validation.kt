package de.familyplanner.android.feature.meals.domain.plan

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.validationFailure

/** Zeilen des Zutatenfelds: getrimmt, ohne Leerzeilen (wie der Server). */
fun ingredientLines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

internal fun validateDish(
    name: String,
    ingredientsText: String,
    ingredientsRequired: Boolean = false,
): ApiResult.Failure? {
    val rawLines = ingredientsText.lines()
    val lines = ingredientLines(ingredientsText)
    return when {
        name.isBlank() -> validationFailure("Bitte einen Namen eingeben.")
        name.trim().length > PlanLimits.NAME_MAX -> validationFailure("Der Name darf höchstens ${PlanLimits.NAME_MAX} Zeichen haben.")
        lines.any { it.length > PlanLimits.INGREDIENT_MAX } ->
            validationFailure("Eine Zutat darf höchstens ${PlanLimits.INGREDIENT_MAX} Zeichen haben.")
        rawLines.size > PlanLimits.INGREDIENT_LINES_RAW_MAX || lines.size > PlanLimits.INGREDIENTS_MAX_COUNT ->
            validationFailure("Höchstens ${PlanLimits.INGREDIENTS_MAX_COUNT} Zutaten je Gericht.")
        ingredientsRequired && lines.isEmpty() -> validationFailure("Bitte mindestens eine Zutat angeben.")
        else -> null
    }
}

internal fun validateReason(reason: String): ApiResult.Failure? =
    when {
        reason.isBlank() -> validationFailure("Bitte eine Begründung angeben.")
        reason.trim().length > PlanLimits.REASON_MAX ->
            validationFailure("Die Begründung darf höchstens ${PlanLimits.REASON_MAX} Zeichen haben.")
        else -> null
    }
