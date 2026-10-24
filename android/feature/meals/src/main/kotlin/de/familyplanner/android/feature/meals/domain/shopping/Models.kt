package de.familyplanner.android.feature.meals.domain.shopping

import de.familyplanner.android.feature.meals.domain.recipe.Ingredient
import de.familyplanner.android.feature.meals.domain.recipe.displayText
import java.time.LocalDate

/** Gericht, aus dem ein Eintrag stammt. */
data class ShoppingSource(
    val dishId: String,
    val dishName: String,
    val date: LocalDate,
)

/** Eintrag der Liste; [itemId] nur bei freien Einträgen, [key] zum Abhaken (vom Server, nie selbst bauen). */
data class ShoppingEntry(
    val key: String,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val checked: Boolean,
    val itemId: String?,
    val sources: List<ShoppingSource>,
) {
    val isFreeItem: Boolean get() = itemId != null

    /** „625 g Vollkornspaghetti“, „Salz“ — gleiche Darstellung wie Zutaten. */
    fun displayText(): String = Ingredient(name, quantity, unit).displayText()
}

data class ShoppingList(
    val weekStart: LocalDate,
    val entries: List<ShoppingEntry>,
)

/** Offene Einträge zuerst, erledigte am Ende — Reihenfolge innerhalb wie vom Server. */
data class ShoppingSections(
    val open: List<ShoppingEntry>,
    val done: List<ShoppingEntry>,
)

fun splitByChecked(entries: List<ShoppingEntry>): ShoppingSections =
    entries.partition { !it.checked }.let { (open, done) -> ShoppingSections(open, done) }
