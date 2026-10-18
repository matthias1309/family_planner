package de.familyplanner.android.feature.meals.fakes

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.feature.meals.domain.recipe.DeleteRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.domain.recipe.LoadRecipeUseCase
import de.familyplanner.android.feature.meals.domain.recipe.LoadRecipesUseCase
import de.familyplanner.android.feature.meals.domain.recipe.Recipe
import de.familyplanner.android.feature.meals.domain.recipe.RecipeDraft
import de.familyplanner.android.feature.meals.domain.recipe.RecipeRepository
import de.familyplanner.android.feature.meals.domain.recipe.RecipeSummary
import de.familyplanner.android.feature.meals.domain.recipe.RecipeUseCases
import de.familyplanner.android.feature.meals.domain.recipe.SaveRecipeUseCase
import kotlinx.coroutines.CompletableDeferred

// Zutatenzeilen sind Fakten (REWE-Seite, geprüft 2026-10-18); die Schritte sind selbst formuliert.
val carbonara =
    Recipe(
        id = "r-1",
        name = "Spaghetti mit Lauch-Carbonara",
        servings = 4,
        ingredients =
            listOf(
                Ingredient("Vollkornspaghetti", 500.0, "g"),
                Ingredient("Salz"),
                Ingredient("Lauch", 2.0, "Stange(n)"),
            ),
        steps = listOf("Nudeln kochen.", "Lauch schneiden."),
        sourceUrl = "https://www.rewe.de/rezepte/spaghetti-lauch-carbonara-mit-ohne-speck/",
        createdBy = "p-1",
    )
val carbonaraSummary = RecipeSummary("r-1", "Spaghetti mit Lauch-Carbonara", 4)
val quesadilla = RecipeSummary("r-2", "Bunte Gemüse Quesadilla", 4)

class FakeRecipeRepository : RecipeRepository {
    var listResult: ApiResult<List<RecipeSummary>> = ApiResult.Success(listOf(quesadilla, carbonaraSummary))
    var recipeResult: (String) -> ApiResult<Recipe> = { ApiResult.Success(carbonara.copy(id = it)) }
    var createResult: ApiResult<Recipe>? = null
    var updateResult: ApiResult<Recipe>? = null
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)

    val requested = mutableListOf<String>()
    val created = mutableListOf<RecipeDraft>()
    val updated = mutableListOf<Pair<String, RecipeDraft>>()
    val deleted = mutableListOf<String>()
    var listCalls = 0

    /** Hält das Speichern zurück (Doppelklick-Test). */
    var createGate: CompletableDeferred<Unit>? = null

    override suspend fun list(): ApiResult<List<RecipeSummary>> {
        listCalls++
        return listResult
    }

    override suspend fun recipe(recipeId: String): ApiResult<Recipe> {
        requested += recipeId
        return recipeResult(recipeId)
    }

    override suspend fun create(draft: RecipeDraft): ApiResult<Recipe> {
        created += draft
        createGate?.await()
        return createResult ?: ApiResult.Success(draft.toRecipe("r-new"))
    }

    override suspend fun update(
        recipeId: String,
        draft: RecipeDraft,
    ): ApiResult<Recipe> {
        updated += recipeId to draft
        return updateResult ?: ApiResult.Success(draft.toRecipe(recipeId))
    }

    override suspend fun delete(recipeId: String): ApiResult<Unit> {
        deleted += recipeId
        return deleteResult
    }

    private fun RecipeDraft.toRecipe(id: String) = Recipe(id, name, servings, ingredients, steps, sourceUrl, "p-1")
}

fun recipeUseCases(repository: RecipeRepository) =
    RecipeUseCases(
        LoadRecipesUseCase(repository),
        LoadRecipeUseCase(repository),
        SaveRecipeUseCase(repository),
        DeleteRecipeUseCase(repository),
    )
