package de.familyplanner.meals.fakes

import de.familyplanner.meals.plan.Dish
import de.familyplanner.meals.plan.DishId
import de.familyplanner.meals.plan.MealPlanRepository
import de.familyplanner.meals.plan.SaveOutcome
import java.time.Instant
import java.time.LocalDate

class InMemoryMealPlanRepository : MealPlanRepository {
    private val dishes = mutableMapOf<DishId, Dish>()

    override suspend fun findBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<Dish> = dishes.values.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }

    override suspend fun createIfDayFree(
        dish: Dish,
        at: Instant,
    ): SaveOutcome {
        if (dishes.values.any { it.date == dish.date }) return SaveOutcome.DAY_TAKEN
        dishes[dish.id] = dish
        return SaveOutcome.SAVED
    }

    override suspend fun updateIfDayFree(
        dish: Dish,
        at: Instant,
    ): SaveOutcome {
        if (dish.id !in dishes) return SaveOutcome.NOT_FOUND
        if (dishes.values.any { it.date == dish.date && it.id != dish.id }) return SaveOutcome.DAY_TAKEN
        dishes[dish.id] = dish
        return SaveOutcome.SAVED
    }

    override suspend fun delete(id: DishId): Boolean = dishes.remove(id) != null
}
