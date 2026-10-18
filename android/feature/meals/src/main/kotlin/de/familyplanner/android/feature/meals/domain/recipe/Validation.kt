package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.validationFailure
import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.domain.plan.ingredientLines
import de.familyplanner.android.feature.meals.domain.plan.validateDish
import java.net.URI
import java.net.URISyntaxException

/** Schritte: eine Zeile je Schritt, getrimmt, ohne Leerzeilen (wie der Server). */
fun stepLines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

/** Reihenfolge der Meldungen wie im Backend: Name → Portionen → Zutaten → Schritte → Link. */
internal fun validateRecipe(
    name: String,
    servings: Int,
    ingredientsText: String,
    stepsText: String,
    sourceUrl: String,
): ApiResult.Failure? =
    when {
        name.isBlank() -> validationFailure("Bitte einen Namen eingeben.")
        name.trim().length > PlanLimits.NAME_MAX -> validationFailure("Der Name darf höchstens ${PlanLimits.NAME_MAX} Zeichen haben.")
        servings !in RecipeLimits.SERVINGS_MIN..RecipeLimits.SERVINGS_MAX ->
            validationFailure("Portionen müssen zwischen ${RecipeLimits.SERVINGS_MIN} und ${RecipeLimits.SERVINGS_MAX} liegen.")
        ingredientLines(ingredientsText).isEmpty() -> validationFailure("Bitte mindestens eine Zutat angeben.")
        else -> validateDish(name, ingredientsText) ?: validateSteps(stepsText) ?: validateSourceUrl(sourceUrl.trim())
    }

private fun validateSteps(text: String): ApiResult.Failure? {
    val steps = stepLines(text)
    return when {
        text.lines().size > RecipeLimits.STEP_LINES_RAW_MAX || steps.size > RecipeLimits.STEPS_MAX_COUNT ->
            validationFailure("Höchstens ${RecipeLimits.STEPS_MAX_COUNT} Schritte.")
        steps.any { it.length > RecipeLimits.STEP_MAX } ->
            validationFailure("Ein Schritt darf höchstens ${RecipeLimits.STEP_MAX} Zeichen haben.")
        else -> null
    }
}

private fun validateSourceUrl(url: String): ApiResult.Failure? =
    if (url.isEmpty() || (url.length <= RecipeLimits.SOURCE_URL_MAX && isHttpUrl(url))) {
        null
    } else {
        validationFailure("Bitte einen gültigen Link (http/https) angeben.")
    }

private fun isHttpUrl(url: String): Boolean =
    try {
        val uri = URI(url)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
    } catch (
        @Suppress("SwallowedException") e: URISyntaxException,
    ) {
        false
    }
