package de.familyplanner.android.feature.meals.domain.recipe

/** Zutat (ADR-0012): Name Pflicht, Menge und Einheit optional. Eine Freitextzeile ist eine Zutat nur mit Name. */
data class Ingredient(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
)

data class RecipeSummary(
    val id: String,
    val name: String,
    val servings: Int,
)

data class Recipe(
    val id: String,
    val name: String,
    val servings: Int,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val sourceUrl: String?,
    val createdBy: String,
)

/** Eingabe für Anlegen/Ändern (bereits bereinigt). */
data class RecipeDraft(
    val name: String,
    val servings: Int,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val sourceUrl: String?,
)

/** Formularwerte des Rezept-Editors (roh, ungeprüft). */
data class RecipeInput(
    val name: String,
    val servings: Int,
    val ingredientsText: String,
    val stepsText: String,
    val sourceUrl: String,
)

/** Am Gericht: „übernommen aus Rezept X für N Portionen“ (ADR-0012). */
data class RecipeRef(
    val recipeId: String,
    val servings: Int,
)
