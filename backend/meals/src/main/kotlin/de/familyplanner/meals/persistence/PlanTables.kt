package de.familyplanner.meals.persistence

import org.jetbrains.exposed.v1.core.Table

object MealDishesTable : Table("meal_dishes") {
    val id = varchar("id", ID_LENGTH)
    val mealDate = varchar("meal_date", DATE_LENGTH)
    val cookMemberId = varchar("cook_member_id", ID_LENGTH)
    val name = varchar("name", NAME_LENGTH)
    val status = varchar("status", STATUS_LENGTH)
    val rejectionReason = varchar("rejection_reason", REASON_LENGTH).nullable()
    val late = bool("late")
    val recipeId = varchar("recipe_id", ID_LENGTH).nullable()
    val servings = integer("servings").nullable()
    val cookedAt = long("cooked_at").nullable()
    val cookedBy = varchar("cooked_by", ID_LENGTH).nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object MealDishIngredientsTable : Table("meal_dish_ingredients") {
    val dishId = varchar("dish_id", ID_LENGTH)
    val position = integer("position")

    /** Ingredient name (column kept from V3). */
    val text = varchar("text", INGREDIENT_LENGTH)
    val quantity = double("quantity").nullable()
    val unit = varchar("unit", UNIT_LENGTH).nullable()

    override val primaryKey = PrimaryKey(dishId, position)
}

object MealCookingCancellationsTable : Table("meal_cooking_cancellations") {
    val memberId = varchar("member_id", ID_LENGTH)
    val weekStart = varchar("week_start", DATE_LENGTH)
    val reason = varchar("reason", REASON_LENGTH)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(memberId, weekStart)
}

private const val STATUS_LENGTH = 9
private const val REASON_LENGTH = 200
private const val ID_LENGTH = 36
private const val DATE_LENGTH = 10
private const val NAME_LENGTH = 60
private const val INGREDIENT_LENGTH = 80
private const val UNIT_LENGTH = 20
