package de.familyplanner.meals.fakes

import de.familyplanner.meals.recipe.Recipe
import de.familyplanner.meals.recipe.RecipeId
import de.familyplanner.meals.recipe.RecipeRepository
import de.familyplanner.meals.recipe.RecipeSaveOutcome
import de.familyplanner.meals.recipe.RecipeSummary
import de.familyplanner.meals.recipe.nameKey
import java.time.Instant

class InMemoryRecipeRepository : RecipeRepository {
    private val recipes = mutableMapOf<RecipeId, Recipe>()

    override suspend fun findAllSummaries(): List<RecipeSummary> = recipes.values.map { RecipeSummary(it.id, it.name, it.servings) }

    override suspend fun findById(id: RecipeId): Recipe? = recipes[id]

    override suspend fun exists(id: RecipeId): Boolean = id in recipes

    override suspend fun createIfNameFree(
        recipe: Recipe,
        at: Instant,
    ): RecipeSaveOutcome {
        if (nameTaken(recipe)) return RecipeSaveOutcome.NAME_TAKEN
        recipes[recipe.id] = recipe
        return RecipeSaveOutcome.SAVED
    }

    override suspend fun updateIfNameFree(
        recipe: Recipe,
        at: Instant,
    ): RecipeSaveOutcome {
        val existing = recipes[recipe.id] ?: return RecipeSaveOutcome.NOT_FOUND
        if (nameTaken(recipe)) return RecipeSaveOutcome.NAME_TAKEN
        recipes[recipe.id] = recipe.copy(createdBy = existing.createdBy)
        return RecipeSaveOutcome.SAVED
    }

    override suspend fun delete(id: RecipeId): Boolean = recipes.remove(id) != null

    private fun nameTaken(recipe: Recipe) = recipes.values.any { it.id != recipe.id && nameKey(it.name) == nameKey(recipe.name) }
}
