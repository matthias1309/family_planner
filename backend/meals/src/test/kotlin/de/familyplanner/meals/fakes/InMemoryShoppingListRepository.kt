package de.familyplanner.meals.fakes

import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.shopping.ShoppingCheck
import de.familyplanner.meals.shopping.ShoppingItem
import de.familyplanner.meals.shopping.ShoppingItemId
import de.familyplanner.meals.shopping.ShoppingListRepository
import java.time.Instant

class InMemoryShoppingListRepository : ShoppingListRepository {
    private val items = mutableListOf<ShoppingItem>()
    private val checks = mutableMapOf<Pair<PlanWeek, String>, ShoppingCheck>()

    override suspend fun findItems(week: PlanWeek): List<ShoppingItem> = items.filter { it.week == week }

    override suspend fun findChecks(week: PlanWeek): List<ShoppingCheck> = checks.filterKeys { it.first == week }.values.toList()

    override suspend fun addItemIfBelowLimit(
        item: ShoppingItem,
        max: Int,
        at: Instant,
    ): Boolean {
        if (items.count { it.week == item.week } >= max) return false
        items += item
        return true
    }

    override suspend fun deleteItem(
        week: PlanWeek,
        id: ShoppingItemId,
    ): Boolean = items.removeIf { it.id == id && it.week == week }

    override suspend fun setItemChecked(
        week: PlanWeek,
        id: ShoppingItemId,
        checked: Boolean,
    ): Boolean {
        val index = items.indexOfFirst { it.id == id && it.week == week }
        if (index < 0) return false
        items[index] = items[index].copy(checked = checked)
        return true
    }

    override suspend fun saveCheck(
        week: PlanWeek,
        check: ShoppingCheck,
        at: Instant,
    ) {
        checks[week to check.key] = check
    }

    override suspend fun deleteCheck(
        week: PlanWeek,
        key: String,
    ) {
        checks.remove(week to key)
    }
}
