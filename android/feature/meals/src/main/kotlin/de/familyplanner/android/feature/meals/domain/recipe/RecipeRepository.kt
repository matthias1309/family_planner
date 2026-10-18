package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.core.data.ApiResult

interface RecipeRepository {
    suspend fun list(): ApiResult<List<RecipeSummary>>

    suspend fun recipe(recipeId: String): ApiResult<Recipe>

    suspend fun create(draft: RecipeDraft): ApiResult<Recipe>

    suspend fun update(
        recipeId: String,
        draft: RecipeDraft,
    ): ApiResult<Recipe>

    suspend fun delete(recipeId: String): ApiResult<Unit>
}
