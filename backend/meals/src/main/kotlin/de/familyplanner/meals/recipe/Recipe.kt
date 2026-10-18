package de.familyplanner.meals.recipe

import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.plan.Ingredient

@JvmInline
value class RecipeId(
    val value: String,
)

data class Recipe(
    val id: RecipeId,
    val name: String,
    val servings: Int,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val sourceUrl: String?,
    val createdBy: MemberId,
)

data class RecipeSummary(
    val id: RecipeId,
    val name: String,
    val servings: Int,
)

/** Result of an atomic recipe write. */
enum class RecipeSaveOutcome { SAVED, NAME_TAKEN, NOT_FOUND }
