package de.familyplanner.android.feature.meals.ui.dish

import java.time.LocalDate

/**
 * Ein Koin-Parameter statt mehrerer Strings (Koin unterscheidet Parameter nach Typ).
 * [dishId] `null` = neues Gericht; [cookId]: Vorauswahl (Stellvertretung / Kind).
 */
data class DishEditArgs(
    val date: LocalDate,
    val dishId: String? = null,
    val cookId: String? = null,
)
