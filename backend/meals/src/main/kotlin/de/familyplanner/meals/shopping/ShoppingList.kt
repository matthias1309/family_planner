package de.familyplanner.meals.shopping

import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.plan.Dish
import de.familyplanner.meals.plan.DishId
import de.familyplanner.meals.plan.DishStatus
import de.familyplanner.meals.plan.Ingredient
import de.familyplanner.meals.recipe.nameKey
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

const val SHOPPING_ITEMS_MAX_COUNT = 50

/** Name and unit escaped (up to twice their length) plus separators. */
const val ENTRY_KEY_MAX = 220

/** Matches the precision of [Ingredient] quantities. */
private const val SUM_SCALE = 3

@JvmInline
value class ShoppingItemId(
    val value: String,
)

/** A free entry a parent added to the week's list. */
data class ShoppingItem(
    val id: ShoppingItemId,
    val week: PlanWeek,
    val ingredient: Ingredient,
    val checked: Boolean = false,
)

/** A stored tick for a computed entry; only valid while the entry still has [quantity]. */
data class ShoppingCheck(
    val key: String,
    val quantity: Double?,
)

data class EntrySource(
    val dishId: DishId,
    val dishName: String,
    val date: LocalDate,
)

data class ShoppingEntry(
    val key: String,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val checked: Boolean,
    /** Set for free entries. */
    val itemId: ShoppingItemId? = null,
    val sources: List<EntrySource> = emptyList(),
)

data class ShoppingList(
    val week: PlanWeek,
    val entries: List<ShoppingEntry>,
)

/** Merges the ingredients of the approved dishes in [week], then appends [items]; see docs/03-design/meals.md. */
fun shoppingListOf(
    week: PlanWeek,
    dishes: List<Dish>,
    items: List<ShoppingItem>,
    checks: List<ShoppingCheck>,
): ShoppingList {
    val approved = dishes.filter { it.status == DishStatus.APPROVED && it.date in week.days }.sortedBy { it.date }
    val computed =
        mergedIngredients(approved)
            .sortedWith(compareBy({ nameKey(it.name) }, { nameKey(it.unit.orEmpty()) }, { it.quantity != null }))
            .map { it.copy(checked = checks.any { check -> check.key == it.key && sameQuantity(check.quantity, it.quantity) }) }
    val free =
        items.map {
            ShoppingEntry("item:${it.id.value}", it.ingredient.name, it.ingredient.quantity, it.ingredient.unit, it.checked, it.id)
        }
    return ShoppingList(week, computed + free)
}

internal fun entryKey(ingredient: Ingredient): String =
    listOf(
        "ing:" + escaped(nameKey(ingredient.name)),
        escaped(nameKey(ingredient.unit.orEmpty())),
        if (ingredient.quantity !=
            null
        ) {
            "q"
        } else {
            "-"
        },
    ).joinToString("|")

/** Escapes the separator so that distinct name/unit pairs can never produce the same key. */
private fun escaped(part: String) = part.replace("\\", "\\\\").replace("|", "\\|")

private class Accumulator(
    val key: String,
    val name: String,
    val unit: String?,
    var sum: BigDecimal?,
    val sources: LinkedHashMap<DishId, EntrySource> = LinkedHashMap(),
)

private fun mergedIngredients(dishes: List<Dish>): List<ShoppingEntry> {
    val merged = LinkedHashMap<String, Accumulator>()
    for (dish in dishes) {
        for (ingredient in dish.ingredients) {
            val acc = merged.getOrPut(entryKey(ingredient)) { Accumulator(entryKey(ingredient), ingredient.name, ingredient.unit, null) }
            ingredient.quantity?.let { acc.sum = (acc.sum ?: BigDecimal.ZERO) + BigDecimal.valueOf(it) }
            acc.sources.putIfAbsent(dish.id, EntrySource(dish.id, dish.name, dish.date))
        }
    }
    return merged.values.map {
        ShoppingEntry(
            it.key,
            it.name,
            it.sum?.setScale(SUM_SCALE, RoundingMode.HALF_UP)?.toDouble(),
            it.unit,
            false,
            null,
            it.sources.values.toList(),
        )
    }
}

private fun sameQuantity(
    checked: Double?,
    current: Double?,
): Boolean {
    if (checked == null || current == null) return checked == current
    return scaled(checked).compareTo(scaled(current)) == 0
}

private fun scaled(value: Double) = BigDecimal.valueOf(value).setScale(SUM_SCALE, RoundingMode.HALF_UP)
