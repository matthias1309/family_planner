package de.familyplanner.android.feature.meals.data.recipe

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.core.data.apiCallNoContent
import de.familyplanner.android.feature.meals.data.mapAllOrNull
import de.familyplanner.android.feature.meals.domain.recipe.Recipe
import de.familyplanner.android.feature.meals.domain.recipe.RecipeDraft
import de.familyplanner.android.feature.meals.domain.recipe.RecipeRepository
import de.familyplanner.android.feature.meals.domain.recipe.RecipeSummary
import de.familyplanner.api.client.MealsApi
import kotlinx.serialization.json.Json

class RemoteRecipeRepository(
    private val meals: MealsApi,
    private val json: Json,
) : RecipeRepository {
    override suspend fun list(): ApiResult<List<RecipeSummary>> =
        apiCall(json, { meals.listRecipes() }) { response -> response.data?.mapAllOrNull { it.toDomain() } }

    override suspend fun recipe(recipeId: String): ApiResult<Recipe> = apiCall(json, { meals.getRecipe(recipeId) }) { it.data?.toDomain() }

    override suspend fun create(draft: RecipeDraft): ApiResult<Recipe> =
        apiCall(json, { meals.createRecipe(draft.toApi()) }) { it.data?.toDomain() }

    override suspend fun update(
        recipeId: String,
        draft: RecipeDraft,
    ): ApiResult<Recipe> = apiCall(json, { meals.updateRecipe(recipeId, draft.toApi()) }) { it.data?.toDomain() }

    override suspend fun delete(recipeId: String): ApiResult<Unit> = apiCallNoContent(json) { meals.deleteRecipe(recipeId) }
}
