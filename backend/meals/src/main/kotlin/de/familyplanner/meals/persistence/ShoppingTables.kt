package de.familyplanner.meals.persistence

import org.jetbrains.exposed.v1.core.Table

object MealShoppingItemsTable : Table("meal_shopping_items") {
    val id = varchar("id", ID_LENGTH)
    val weekStart = varchar("week_start", DATE_LENGTH)
    val name = varchar("name", INGREDIENT_LENGTH)
    val quantity = double("quantity").nullable()
    val unit = varchar("unit", UNIT_LENGTH).nullable()
    val checked = bool("checked")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object MealShoppingChecksTable : Table("meal_shopping_checks") {
    val weekStart = varchar("week_start", DATE_LENGTH)
    val entryKey = varchar("entry_key", ENTRY_KEY_LENGTH)
    val checkedQuantity = double("checked_quantity").nullable()
    val checkedAt = long("checked_at")

    override val primaryKey = PrimaryKey(weekStart, entryKey)
}

private const val ID_LENGTH = 36
private const val DATE_LENGTH = 10
private const val INGREDIENT_LENGTH = 80
private const val UNIT_LENGTH = 20
private const val ENTRY_KEY_LENGTH = 220
