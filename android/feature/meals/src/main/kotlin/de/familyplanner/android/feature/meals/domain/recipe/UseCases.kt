package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError

/** Lädt die Rezeptliste und merkt sie sich für das nächste Öffnen (ADR-0018). */
class LoadRecipesUseCase(
    private val repository: RecipeRepository,
    private val cache: RecipeListCache,
) {
    /** Zuletzt geladener Stand des angemeldeten Mitglieds, sonst `null`. */
    fun cached(): List<RecipeSummary>? = cache.read()

    suspend operator fun invoke(): ApiResult<List<RecipeSummary>> {
        // Vor der Anfrage: Antwort gehört dem Mitglied, das sie angefordert hat (Mitgliederwechsel während des Ladens).
        val ticket = cache.ticket()
        return repository.list().also { if (it is ApiResult.Success) cache.write(ticket, it.data) }
    }
}

/** Name enthält [query] ohne Groß-/Kleinschreibung; leer → alle (wie die Server-Suche, REQ-MEAL-060). Reihenfolge bleibt. */
fun filterRecipes(
    recipes: List<RecipeSummary>,
    query: String,
): List<RecipeSummary> {
    val needle = query.trim()
    return if (needle.isEmpty()) recipes else recipes.filter { it.name.contains(needle, ignoreCase = true) }
}

class LoadRecipeUseCase(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(recipeId: String): ApiResult<Recipe> = repository.recipe(recipeId)
}

class SaveRecipeUseCase(
    private val repository: RecipeRepository,
    private val cache: RecipeListCache,
) {
    suspend operator fun invoke(
        recipeId: String?,
        input: RecipeInput,
    ): ApiResult<Recipe> {
        validateRecipe(input.name, input.servings, input.ingredientsText, input.stepsText, input.sourceUrl)?.let { return it }
        val draft =
            RecipeDraft(
                name = input.name.trim(),
                servings = input.servings,
                ingredients = ingredientsFromText(input.ingredientsText),
                steps = stepLines(input.stepsText),
                sourceUrl = input.sourceUrl.trim().ifEmpty { null },
            )
        val result = if (recipeId == null) repository.create(draft) else repository.update(recipeId, draft)
        if (result is ApiResult.Success) {
            val saved = result.data
            cache.update { it.upsert(RecipeSummary(saved.id, saved.name, saved.servings)) }
        }
        return result
    }
}

class DeleteRecipeUseCase(
    private val repository: RecipeRepository,
    private val cache: RecipeListCache,
) {
    /** Auch „schon gelöscht“ (anderes Gerät) entfernt das Rezept aus der gemerkten Liste. */
    suspend operator fun invoke(recipeId: String): ApiResult<Unit> =
        repository.delete(recipeId).also { result ->
            val gone = result is ApiResult.Success || (result as? ApiResult.Failure)?.error == AppError.NotFound
            if (gone) cache.update { recipes -> recipes.filterNot { it.id == recipeId } }
        }
}

/** Bündelt die Rezept-Use-Cases (hält die ViewModel-Parameterlisten kurz). */
class RecipeUseCases(
    val list: LoadRecipesUseCase,
    val load: LoadRecipeUseCase,
    val save: SaveRecipeUseCase,
    val delete: DeleteRecipeUseCase,
)
