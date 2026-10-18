package de.familyplanner.meals.persistence

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.plan.Cancellation
import de.familyplanner.meals.plan.Dish
import de.familyplanner.meals.plan.DishId
import de.familyplanner.meals.plan.DishStatus
import de.familyplanner.meals.plan.MealPlanRepository
import de.familyplanner.meals.plan.ReviewOutcome
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
import org.jetbrains.exposed.v1.jdbc.upsert
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

    override suspend fun findById(id: DishId): Dish? =
        dbQuery(db) {
            MealDishesTable
                .selectAll()
                .where { MealDishesTable.id eq id.value }
                .firstOrNull()
                ?.let { it.toDish(ingredientsOf(listOf(id.value))[id.value].orEmpty()) }
        }

    override suspend fun createIfDayFree(
        dish: Dish,
        at: Instant,
        cookWeek: PlanWeek?,
    ): SaveOutcome =
        dbQuery(db) {
            val taken = MealDishesTable.selectAll().where { MealDishesTable.mealDate eq dish.date.toString() }.any()
            if (taken) return@dbQuery SaveOutcome.DAY_TAKEN
            cookWeek?.let { cookWeekOutcome(dish, it) }?.let { return@dbQuery it }
            MealDishesTable.insert {
                it[id] = dish.id.value
                it[mealDate] = dish.date.toString()
                it[cookMemberId] = dish.cookId.value
                it[name] = dish.name
                it[status] = dish.status.name
                it[rejectionReason] = dish.rejectionReason
                it[late] = dish.late
                it[createdAt] = at.toEpochMilli()
                it[updatedAt] = at.toEpochMilli()
            }
            insertIngredients(dish)
            SaveOutcome.SAVED
        }

    override suspend fun updateIfDayFree(
        dish: Dish,
        at: Instant,
        cookWeek: PlanWeek?,
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
            cookWeek?.let { cookWeekOutcome(dish, it) }?.let { return@dbQuery it }
            MealDishesTable.update({ MealDishesTable.id eq dish.id.value }) {
                it[mealDate] = dish.date.toString()
                it[cookMemberId] = dish.cookId.value
                it[name] = dish.name
                it[status] = dish.status.name
                it[rejectionReason] = dish.rejectionReason
                it[late] = dish.late
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

    override suspend fun review(
        id: DishId,
        status: DishStatus,
        reason: String?,
        at: Instant,
    ): ReviewOutcome =
        dbQuery(db) {
            val current = MealDishesTable.selectAll().where { MealDishesTable.id eq id.value }.firstOrNull()
            when {
                current == null -> ReviewOutcome.NOT_FOUND
                current[MealDishesTable.status] != DishStatus.SUBMITTED.name -> ReviewOutcome.NOT_PENDING
                else -> {
                    MealDishesTable.update({ MealDishesTable.id eq id.value }) {
                        it[MealDishesTable.status] = status.name
                        it[rejectionReason] = reason
                        it[updatedAt] = at.toEpochMilli()
                    }
                    ReviewOutcome.SAVED
                }
            }
        }

    override suspend fun findCancellations(week: PlanWeek): List<Cancellation> =
        dbQuery(db) {
            MealCookingCancellationsTable
                .selectAll()
                .where { MealCookingCancellationsTable.weekStart eq week.monday.toString() }
                .map { it.toCancellation() }
        }

    override suspend fun cancel(cancellation: Cancellation) =
        dbQuery(db) {
            MealCookingCancellationsTable.upsert {
                it[memberId] = cancellation.memberId.value
                it[weekStart] = cancellation.week.monday.toString()
                it[reason] = cancellation.reason
                it[createdAt] = cancellation.createdAt.toEpochMilli()
            }
            val dishIds =
                cookDishesIn(cancellation.memberId, cancellation.week)
                    .map { it[MealDishesTable.id] }
            MealDishIngredientsTable.deleteWhere { MealDishIngredientsTable.dishId inList dishIds }
            MealDishesTable.deleteWhere { MealDishesTable.id inList dishIds }
            Unit
        }

    override suspend fun deleteCancellation(
        memberId: MemberId,
        week: PlanWeek,
    ): Boolean =
        dbQuery(db) {
            MealCookingCancellationsTable.deleteWhere {
                (MealCookingCancellationsTable.memberId eq memberId.value) and
                    (MealCookingCancellationsTable.weekStart eq week.monday.toString())
            } > 0
        }

    /** Null if the cook may take another dish in [week]. */
    private fun JdbcTransaction.cookWeekOutcome(
        dish: Dish,
        week: PlanWeek,
    ): SaveOutcome? {
        val otherDish = cookDishesIn(dish.cookId, week).any { it[MealDishesTable.id] != dish.id.value }
        if (otherDish) return SaveOutcome.COOK_WEEK_TAKEN
        val cancelled =
            MealCookingCancellationsTable
                .selectAll()
                .where {
                    (MealCookingCancellationsTable.memberId eq dish.cookId.value) and
                        (MealCookingCancellationsTable.weekStart eq week.monday.toString())
                }.any()
        return if (cancelled) SaveOutcome.COOK_CANCELLED else null
    }

    private fun JdbcTransaction.cookDishesIn(
        cookId: MemberId,
        week: PlanWeek,
    ) = MealDishesTable
        .selectAll()
        .where {
            (MealDishesTable.cookMemberId eq cookId.value) and
                (MealDishesTable.mealDate greaterEq week.monday.toString()) and
                (MealDishesTable.mealDate lessEq week.days.last().toString())
        }.toList()

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
        status = DishStatus.valueOf(this[MealDishesTable.status]),
        rejectionReason = this[MealDishesTable.rejectionReason],
        late = this[MealDishesTable.late],
    )

internal fun ResultRow.toCancellation() =
    Cancellation(
        memberId = MemberId(this[MealCookingCancellationsTable.memberId]),
        week = PlanWeek(LocalDate.parse(this[MealCookingCancellationsTable.weekStart])),
        reason = this[MealCookingCancellationsTable.reason],
        createdAt = Instant.ofEpochMilli(this[MealCookingCancellationsTable.createdAt]),
    )
