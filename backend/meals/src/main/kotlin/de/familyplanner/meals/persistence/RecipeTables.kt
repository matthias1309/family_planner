package de.familyplanner.meals.persistence

import org.jetbrains.exposed.v1.core.Table

object MealRecipesTable : Table("meal_recipes") {
    val id = varchar("id", ID_LENGTH)
    val name = varchar("name", NAME_LENGTH)
    val nameKey = varchar("name_key", NAME_LENGTH)
    val servings = integer("servings")
    val sourceUrl = varchar("source_url", SOURCE_URL_LENGTH).nullable()
    val createdBy = varchar("created_by", ID_LENGTH)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object MealRecipeIngredientsTable : Table("meal_recipe_ingredients") {
    val recipeId = varchar("recipe_id", ID_LENGTH)
    val position = integer("position")
    val name = varchar("name", INGREDIENT_LENGTH)
    val quantity = double("quantity").nullable()
    val unit = varchar("unit", UNIT_LENGTH).nullable()

    override val primaryKey = PrimaryKey(recipeId, position)
}

object MealRecipeStepsTable : Table("meal_recipe_steps") {
    val recipeId = varchar("recipe_id", ID_LENGTH)
    val position = integer("position")
    val text = varchar("text", STEP_LENGTH)

    override val primaryKey = PrimaryKey(recipeId, position)
}

private const val ID_LENGTH = 36
private const val NAME_LENGTH = 60
private const val INGREDIENT_LENGTH = 80
private const val UNIT_LENGTH = 20
private const val STEP_LENGTH = 1000
private const val SOURCE_URL_LENGTH = 500
