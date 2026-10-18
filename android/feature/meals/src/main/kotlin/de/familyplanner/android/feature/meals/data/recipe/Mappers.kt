package de.familyplanner.android.feature.meals.data.recipe

import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.domain.recipe.Recipe
import de.familyplanner.android.feature.meals.domain.recipe.RecipeDraft
import de.familyplanner.android.feature.meals.domain.recipe.RecipeLimits
import de.familyplanner.android.feature.meals.domain.recipe.RecipeSummary
import de.familyplanner.api.model.SaveRecipeRequest
import de.familyplanner.api.model.Ingredient as ApiIngredient
import de.familyplanner.api.model.Recipe as ApiRecipe
import de.familyplanner.api.model.RecipeSummary as ApiRecipeSummary

internal fun ApiIngredient.toDomain() = Ingredient(name, quantity, unit)

internal fun Ingredient.toApi() = ApiIngredient(name, quantity, unit)

/** `null` bei unlesbaren Daten (Portionen < 1): Serverfehler statt Absturz. */
internal fun ApiRecipeSummary.toDomain(): RecipeSummary? =
    RecipeSummary(id, name, servings).takeIf {
        servings >= RecipeLimits.SERVINGS_MIN
    }

internal fun ApiRecipe.toDomain(): Recipe? =
    Recipe(id, name, servings, ingredients.map { it.toDomain() }, steps, sourceUrl, createdBy)
        .takeIf { servings >= RecipeLimits.SERVINGS_MIN }

internal fun RecipeDraft.toApi() = SaveRecipeRequest(name, servings, ingredients.map { it.toApi() }, steps, sourceUrl)
