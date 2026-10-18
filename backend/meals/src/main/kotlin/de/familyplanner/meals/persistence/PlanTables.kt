package de.familyplanner.meals.persistence

import org.jetbrains.exposed.v1.core.Table

object MealDishesTable : Table("meal_dishes") {
    val id = varchar("id", ID_LENGTH)
    val mealDate = varchar("meal_date", DATE_LENGTH)
    val cookMemberId = varchar("cook_member_id", ID_LENGTH)
    val name = varchar("name", NAME_LENGTH)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object MealDishIngredientsTable : Table("meal_dish_ingredients") {
    val dishId = varchar("dish_id", ID_LENGTH)
    val position = integer("position")
    val text = varchar("text", INGREDIENT_LENGTH)

    override val primaryKey = PrimaryKey(dishId, position)
}

private const val ID_LENGTH = 36
private const val DATE_LENGTH = 10
private const val NAME_LENGTH = 60
private const val INGREDIENT_LENGTH = 80
