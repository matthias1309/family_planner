package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.feature.meals.domain.plan.PlanLimits

/** Rohdaten eines gefundenen schema.org-Recipe (noch nicht zerlegt). */
data class SourceRecipe(
    val name: String,
    val servings: Int?,
    val ingredientLines: List<String>,
    val steps: List<String>,
)

enum class RecipeImportError { NO_LINK, UNREACHABLE, NO_RECIPE }

sealed interface SourceResult {
    data class Found(
        val recipe: SourceRecipe,
    ) : SourceResult

    data object NotFound : SourceResult

    data object Unreachable : SourceResult
}

/** Liest ein Rezept von einer Webseite (App-seitig, ADR-0012). */
interface RecipeSource {
    suspend fun read(url: String): SourceResult
}

sealed interface RecipeImportResult {
    data class Success(
        val input: RecipeInput,
    ) : RecipeImportResult

    data class Failure(
        val error: RecipeImportError,
    ) : RecipeImportResult
}

/** Text/Link → Formularwerte für den Rezept-Editor; gespeichert wird erst dort (ADR-0012). */
class ImportRecipeUseCase(
    private val source: RecipeSource,
) {
    suspend operator fun invoke(text: String): RecipeImportResult {
        val url = extractRecipeUrl(text) ?: return RecipeImportResult.Failure(RecipeImportError.NO_LINK)
        return when (val result = source.read(url)) {
            is SourceResult.Found -> RecipeImportResult.Success(result.recipe.toInput(url))
            SourceResult.NotFound -> RecipeImportResult.Failure(RecipeImportError.NO_RECIPE)
            SourceResult.Unreachable -> RecipeImportResult.Failure(RecipeImportError.UNREACHABLE)
        }
    }
}

// Marken erst nach dem Zerlegen am Namen entfernen, sonst würde „1.0 Dose(n) REWE …“ falsch zerlegt.
private fun SourceRecipe.toInput(url: String) =
    RecipeInput(
        name = name.trim().take(PlanLimits.NAME_MAX),
        servings = (servings ?: RecipeLimits.SERVINGS_DEFAULT).coerceIn(RecipeLimits.SERVINGS_MIN, RecipeLimits.SERVINGS_MAX),
        ingredientsText = ingredientsToText(ingredientLines.map(::parseIngredientLine).map { it.copy(name = stripStoreBrand(it.name)) }),
        stepsText = steps.joinToString("\n"),
        sourceUrl = url,
    )
