package de.familyplanner.android.feature.meals.domain.recipe

/** Rechnet Mengen von [base] auf [target] Portionen um (3 Nachkommastellen); Zutaten ohne Menge bleiben ohne Menge (REQ-MEAL-062). */
fun scaleIngredients(
    ingredients: List<Ingredient>,
    base: Int,
    target: Int,
): List<Ingredient> =
    ingredients.map { ingredient ->
        ingredient.quantity?.let { ingredient.copy(quantity = roundedQuantity(it * target / base)) } ?: ingredient
    }

/** Vorschlag: an dem Tag anwesende Mitglieder, begrenzt auf 1–20 (REQ-MEAL-062). */
fun defaultServings(presentMembers: Int): Int = presentMembers.coerceIn(RecipeLimits.SERVINGS_MIN, RecipeLimits.SERVINGS_MAX)
