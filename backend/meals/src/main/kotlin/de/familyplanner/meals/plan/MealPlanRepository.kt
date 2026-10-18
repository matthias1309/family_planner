package de.familyplanner.meals.plan

import java.time.Instant
import java.time.LocalDate

interface MealPlanRepository {
    /** Dishes dated within the inclusive range, sorted by date, ingredients in input order. */
    suspend fun findBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<Dish>

    /** Atomically inserts unless the day already has a dish; never returns [SaveOutcome.NOT_FOUND]. */
    suspend fun createIfDayFree(
        dish: Dish,
        at: Instant,
    ): SaveOutcome

    /** Atomically replaces the dish with the same id unless another dish occupies its (new) date. */
    suspend fun updateIfDayFree(
        dish: Dish,
        at: Instant,
    ): SaveOutcome

    suspend fun delete(id: DishId): Boolean
}
