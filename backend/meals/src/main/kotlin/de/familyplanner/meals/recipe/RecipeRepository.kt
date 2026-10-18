package de.familyplanner.meals.recipe

import java.time.Instant

interface RecipeRepository {
    suspend fun findAllSummaries(): List<RecipeSummary>

    suspend fun findById(id: RecipeId): Recipe?

    suspend fun exists(id: RecipeId): Boolean

    /** Atomically inserts unless another recipe has the same [nameKey]; never [RecipeSaveOutcome.NOT_FOUND]. */
    suspend fun createIfNameFree(
        recipe: Recipe,
        at: Instant,
    ): RecipeSaveOutcome

    /** Atomically replaces the content (not `createdBy`) unless the name belongs to another recipe. */
    suspend fun updateIfNameFree(
        recipe: Recipe,
        at: Instant,
    ): RecipeSaveOutcome

    /** Deletes the recipe with ingredients and steps and clears the recipe reference on dishes; false if unknown. */
    suspend fun delete(id: RecipeId): Boolean
}
