package de.familyplanner.meals.persistence

import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.plan.Ingredient
import de.familyplanner.meals.shopping.ShoppingCheck
import de.familyplanner.meals.shopping.ShoppingItem
import de.familyplanner.meals.shopping.ShoppingItemId
import de.familyplanner.meals.shopping.ShoppingListRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalDate

class ExposedShoppingListRepository(
    private val db: Database,
) : ShoppingListRepository {
    override suspend fun findItems(week: PlanWeek): List<ShoppingItem> =
        dbQuery(db) {
            MealShoppingItemsTable
                .selectAll()
                .where { MealShoppingItemsTable.weekStart eq week.monday.toString() }
                .orderBy(MealShoppingItemsTable.createdAt to SortOrder.ASC, MealShoppingItemsTable.id to SortOrder.ASC)
                .map { it.toItem() }
        }

    override suspend fun findChecks(week: PlanWeek): List<ShoppingCheck> =
        dbQuery(db) {
            MealShoppingChecksTable
                .selectAll()
                .where { MealShoppingChecksTable.weekStart eq week.monday.toString() }
                .map { ShoppingCheck(it[MealShoppingChecksTable.entryKey], it[MealShoppingChecksTable.checkedQuantity]) }
        }

    override suspend fun addItemIfBelowLimit(
        item: ShoppingItem,
        max: Int,
        at: Instant,
    ): Boolean =
        dbQuery(db) {
            val count = MealShoppingItemsTable.selectAll().where { MealShoppingItemsTable.weekStart eq item.week.monday.toString() }.count()
            if (count >= max) return@dbQuery false
            MealShoppingItemsTable.insert {
                it[id] = item.id.value
                it[weekStart] = item.week.monday.toString()
                it[name] = item.ingredient.name
                it[quantity] = item.ingredient.quantity
                it[unit] = item.ingredient.unit
                it[checked] = item.checked
                it[createdAt] = at.toEpochMilli()
            }
            true
        }

    override suspend fun deleteItem(
        week: PlanWeek,
        id: ShoppingItemId,
    ): Boolean =
        dbQuery(db) {
            MealShoppingItemsTable.deleteWhere {
                (MealShoppingItemsTable.id eq id.value) and (MealShoppingItemsTable.weekStart eq week.monday.toString())
            } > 0
        }

    override suspend fun setItemChecked(
        week: PlanWeek,
        id: ShoppingItemId,
        checked: Boolean,
    ): Boolean =
        dbQuery(db) {
            MealShoppingItemsTable.update({
                (MealShoppingItemsTable.id eq id.value) and (MealShoppingItemsTable.weekStart eq week.monday.toString())
            }) { it[MealShoppingItemsTable.checked] = checked } > 0
        }

    override suspend fun saveCheck(
        week: PlanWeek,
        check: ShoppingCheck,
        at: Instant,
    ) {
        dbQuery(db) {
            deleteCheckRows(week, check.key)
            MealShoppingChecksTable.insert {
                it[weekStart] = week.monday.toString()
                it[entryKey] = check.key
                it[checkedQuantity] = check.quantity
                it[checkedAt] = at.toEpochMilli()
            }
        }
    }

    override suspend fun deleteCheck(
        week: PlanWeek,
        key: String,
    ) {
        dbQuery(db) { deleteCheckRows(week, key) }
    }

    private fun deleteCheckRows(
        week: PlanWeek,
        key: String,
    ) = MealShoppingChecksTable.deleteWhere {
        (MealShoppingChecksTable.weekStart eq week.monday.toString()) and (MealShoppingChecksTable.entryKey eq key)
    }
}

private fun ResultRow.toItem() =
    ShoppingItem(
        id = ShoppingItemId(this[MealShoppingItemsTable.id]),
        week = PlanWeek(LocalDate.parse(this[MealShoppingItemsTable.weekStart])),
        ingredient =
            Ingredient(
                this[MealShoppingItemsTable.name],
                this[MealShoppingItemsTable.quantity],
                this[MealShoppingItemsTable.unit],
            ),
        checked = this[MealShoppingItemsTable.checked],
    )
