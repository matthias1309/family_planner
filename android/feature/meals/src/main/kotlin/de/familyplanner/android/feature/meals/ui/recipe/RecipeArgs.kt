package de.familyplanner.android.feature.meals.ui.recipe

import java.time.LocalDate

/** Je ein Koin-Parameter (Koin unterscheidet Parameter nach Typ). [pick]: Rezept für den Gericht-Editor wählen. */
data class RecipeListArgs(
    val pick: Boolean = false,
)

/** [dishDate]/[dishId]: Ansicht zu einem Gericht — Zutaten mit dessen Mengen (REQ-MEAL-063). */
data class RecipeViewArgs(
    val recipeId: String,
    val dishDate: LocalDate? = null,
    val dishId: String? = null,
)

/** [recipeId] `null` = neues Rezept. */
data class RecipeEditArgs(
    val recipeId: String? = null,
)
