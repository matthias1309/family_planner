package de.familyplanner.meals.web

import de.familyplanner.meals.plan.Ingredient
import de.familyplanner.meals.recipe.Recipe
import de.familyplanner.meals.recipe.RecipeSummary
import de.familyplanner.api.model.Ingredient as ApiIngredient
import de.familyplanner.api.model.Recipe as ApiRecipe
import de.familyplanner.api.model.RecipeSummary as ApiRecipeSummary

fun Ingredient.toApi() = ApiIngredient(name = name, quantity = quantity, unit = unit)

fun ApiIngredient.toDomain() = Ingredient(name, quantity, unit)

fun Recipe.toApi() =
    ApiRecipe(
        id = id.value,
        name = name,
        servings = servings,
        ingredients = ingredients.map { it.toApi() },
        steps = steps,
        createdBy = createdBy.value,
        sourceUrl = sourceUrl,
    )

fun RecipeSummary.toApi() = ApiRecipeSummary(id = id.value, name = name, servings = servings)
