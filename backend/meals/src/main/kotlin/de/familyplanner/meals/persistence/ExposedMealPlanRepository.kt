package de.familyplanner.meals.persistence

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.meals.plan.Dish
import de.familyplanner.meals.plan.DishId
import de.familyplanner.meals.plan.MealPlanRepository
import de.familyplanner.meals.plan.SaveOutcome
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalDate

class ExposedMealPlanRepository(
    private val db: Database,
) : MealPlanRepository {
    /** Compares ISO date text; valid because the web layer only admits four-digit years (see `MIN_SUPPORTED_YEAR`). */
    override suspend fun findBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<Dish> =
        dbQuery(db) {
            val rows =
                MealDishesTable
                    .selectAll()
                    .where { (MealDishesTable.mealDate greaterEq from.toString()) and (MealDishesTable.mealDate lessEq to.toString()) }
                    .orderBy(MealDishesTable.mealDate)
                    .toList()
            val ingredients = ingredientsOf(rows.map { it[MealDishesTable.id] })
            rows.map { it.toDish(ingredients[it[MealDishesTable.id]].orEmpty()) }
        }

    override suspend fun createIfDayFree(
        dish: Dish,
        at: Instant,
    ): SaveOutcome =
        dbQuery(db) {
            val taken = MealDishesTable.selectAll().where { MealDishesTable.mealDate eq dish.date.toString() }.any()
            if (taken) return@dbQuery SaveOutcome.DAY_TAKEN
            MealDishesTable.insert {
                it[id] = dish.id.value
                it[mealDate] = dish.date.toString()
                it[cookMemberId] = dish.cookId.value
                it[name] = dish.name
                it[createdAt] = at.toEpochMilli()
                it[updatedAt] = at.toEpochMilli()
            }
            insertIngredients(dish)
            SaveOutcome.SAVED
        }

    override suspend fun updateIfDayFree(
        dish: Dish,
        at: Instant,
    ): SaveOutcome =
        dbQuery(db) {
            val exists = MealDishesTable.selectAll().where { MealDishesTable.id eq dish.id.value }.any()
            if (!exists) return@dbQuery SaveOutcome.NOT_FOUND
            val taken =
                MealDishesTable
                    .selectAll()
                    .where { (MealDishesTable.mealDate eq dish.date.toString()) and (MealDishesTable.id neq dish.id.value) }
                    .any()
            if (taken) return@dbQuery SaveOutcome.DAY_TAKEN
            MealDishesTable.update({ MealDishesTable.id eq dish.id.value }) {
                it[mealDate] = dish.date.toString()
                it[cookMemberId] = dish.cookId.value
                it[name] = dish.name
                it[updatedAt] = at.toEpochMilli()
            }
            MealDishIngredientsTable.deleteWhere { MealDishIngredientsTable.dishId eq dish.id.value }
            insertIngredients(dish)
            SaveOutcome.SAVED
        }

    override suspend fun delete(id: DishId): Boolean =
        dbQuery(db) {
            MealDishIngredientsTable.deleteWhere { MealDishIngredientsTable.dishId eq id.value }
            MealDishesTable.deleteWhere { MealDishesTable.id eq id.value } > 0
        }

    private fun JdbcTransaction.insertIngredients(dish: Dish) {
        MealDishIngredientsTable.batchInsert(dish.ingredients.withIndex()) { (index, line) ->
            this[MealDishIngredientsTable.dishId] = dish.id.value
            this[MealDishIngredientsTable.position] = index
            this[MealDishIngredientsTable.text] = line
        }
    }

    private fun ingredientsOf(dishIds: List<String>): Map<String, List<String>> {
        if (dishIds.isEmpty()) return emptyMap()
        return MealDishIngredientsTable
            .selectAll()
            .where { MealDishIngredientsTable.dishId inList dishIds }
            .orderBy(MealDishIngredientsTable.position)
            .groupBy({ it[MealDishIngredientsTable.dishId] }, { it[MealDishIngredientsTable.text] })
    }
}

internal fun ResultRow.toDish(ingredients: List<String>) =
    Dish(
        id = DishId(this[MealDishesTable.id]),
        date = LocalDate.parse(this[MealDishesTable.mealDate]),
        cookId = MemberId(this[MealDishesTable.cookMemberId]),
        name = this[MealDishesTable.name],
        ingredients = ingredients,
    )
