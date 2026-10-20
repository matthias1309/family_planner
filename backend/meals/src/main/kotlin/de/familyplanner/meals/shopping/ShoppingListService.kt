package de.familyplanner.meals.shopping

import de.familyplanner.core.error.AppException
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.plan.Ingredient
import de.familyplanner.meals.plan.MealPlanRepository
import de.familyplanner.meals.plan.validatedIngredients
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class ShoppingListService(
    private val plan: MealPlanRepository,
    private val shopping: ShoppingListRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(ShoppingListService::class.java)

    suspend fun list(week: PlanWeek): ShoppingList =
        shoppingListOf(week, plan.findBetween(week.monday, week.days.last()), shopping.findItems(week), shopping.findChecks(week))

    suspend fun addItem(
        week: PlanWeek,
        ingredient: Ingredient,
    ): ShoppingList {
        val item = ShoppingItem(ShoppingItemId(newId()), week, validatedIngredients(listOf(ingredient)).single())
        if (!shopping.addItemIfBelowLimit(item, SHOPPING_ITEMS_MAX_COUNT, clock.instant())) {
            throw AppException.Validation("Höchstens $SHOPPING_ITEMS_MAX_COUNT freie Einträge")
        }
        log.info("Shopping item added: {} {}", week.monday, item.id.value)
        return list(week)
    }

    suspend fun deleteItem(
        week: PlanWeek,
        id: ShoppingItemId,
    ) {
        if (!shopping.deleteItem(week, id)) throw AppException.NotFound(ENTRY_NOT_FOUND)
        log.info("Shopping item deleted: {}", id.value)
    }

    suspend fun setChecked(
        week: PlanWeek,
        key: String,
        checked: Boolean,
    ): ShoppingList {
        requireValidKey(key)
        val entry = list(week).entries.firstOrNull { it.key == key } ?: throw AppException.NotFound(ENTRY_NOT_FOUND)
        val itemId = entry.itemId
        when {
            itemId != null -> checkItem(week, itemId, checked)
            checked -> shopping.saveCheck(week, ShoppingCheck(key, entry.quantity), clock.instant())
            else -> shopping.deleteCheck(week, key)
        }
        log.info("Shopping entry checked: {} {}", week.monday, checked)
        return list(week)
    }

    private suspend fun checkItem(
        week: PlanWeek,
        id: ShoppingItemId,
        checked: Boolean,
    ) {
        if (!shopping.setItemChecked(week, id, checked)) throw AppException.NotFound(ENTRY_NOT_FOUND)
    }

    private fun requireValidKey(key: String) {
        if (key.isBlank() || key.length > ENTRY_KEY_MAX) throw AppException.Validation("Ungültiger Eintrag")
    }

    private companion object {
        const val ENTRY_NOT_FOUND = "Eintrag nicht gefunden"
    }
}
