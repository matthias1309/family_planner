package de.familyplanner.android.feature.meals.ui.dish

import de.familyplanner.android.feature.meals.domain.plan.PlanLimits
import de.familyplanner.android.feature.meals.domain.recipe.Recipe
import de.familyplanner.android.feature.meals.domain.recipe.RecipeLimits
import de.familyplanner.android.feature.meals.domain.recipe.defaultServings
import de.familyplanner.android.feature.meals.domain.recipe.ingredientsToText
import de.familyplanner.android.feature.meals.domain.recipe.scaleIngredients

/** Rezept gewählt: Portionen-Vorschlag = Anwesende des Tages, Name und Zutaten (umgerechnet) werden übernommen (REQ-MEAL-062). */
internal fun DishEditUiState.withPickedRecipe(recipe: Recipe): DishEditUiState {
    val servings = defaultServings(presentByDate[date] ?: 0)
    return copy(
        isRecipeLoading = false,
        recipe = recipe,
        recipeId = recipe.id,
        servings = servings,
        name = recipe.name.take(PlanLimits.NAME_MAX),
        ingredientsText = ingredientsFor(recipe, servings),
    )
}

/** Portionen geändert: Zutaten werden neu aus dem Rezept gerechnet (überschreibt manuelle Änderungen), Name bleibt. */
internal fun DishEditUiState.withServings(value: Int): DishEditUiState {
    val current = recipe ?: return this
    val servings = value.coerceIn(RecipeLimits.SERVINGS_MIN, RecipeLimits.SERVINGS_MAX)
    return copy(servings = servings, ingredientsText = ingredientsFor(current, servings))
}

/** Rezeptbezug entfernt: Name und Zutaten bleiben als Text stehen. */
internal fun DishEditUiState.withoutRecipe(): DishEditUiState = copy(recipeId = null, servings = null, recipe = null)

private fun ingredientsFor(
    recipe: Recipe,
    servings: Int,
): String = ingredientsToText(scaleIngredients(recipe.ingredients, recipe.servings, servings))
