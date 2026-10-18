package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.core.data.ApiResult

class LoadRecipesUseCase(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(): ApiResult<List<RecipeSummary>> = repository.list()
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
        return if (recipeId == null) repository.create(draft) else repository.update(recipeId, draft)
    }
}

class DeleteRecipeUseCase(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(recipeId: String): ApiResult<Unit> = repository.delete(recipeId)
}

/** Bündelt die Rezept-Use-Cases (hält die ViewModel-Parameterlisten kurz). */
class RecipeUseCases(
    val list: LoadRecipesUseCase,
    val load: LoadRecipeUseCase,
    val save: SaveRecipeUseCase,
    val delete: DeleteRecipeUseCase,
)
