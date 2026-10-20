package de.familyplanner.meals.shopping

import de.familyplanner.meals.attendance.PlanWeek
import java.time.Instant

interface ShoppingListRepository {
    /** Free entries of [week] in creation order. */
    suspend fun findItems(week: PlanWeek): List<ShoppingItem>

    suspend fun findChecks(week: PlanWeek): List<ShoppingCheck>

    /** Atomically inserts unless [item]'s week already has [max] items; false if the limit is reached. */
    suspend fun addItemIfBelowLimit(
        item: ShoppingItem,
        max: Int,
        at: Instant,
    ): Boolean

    /** Deletes the item only if it belongs to [week]. */
    suspend fun deleteItem(
        week: PlanWeek,
        id: ShoppingItemId,
    ): Boolean

    suspend fun setItemChecked(
        week: PlanWeek,
        id: ShoppingItemId,
        checked: Boolean,
    ): Boolean

    /** Stores (or replaces) the tick of a computed entry. */
    suspend fun saveCheck(
        week: PlanWeek,
        check: ShoppingCheck,
        at: Instant,
    )

    suspend fun deleteCheck(
        week: PlanWeek,
        key: String,
    )
}
