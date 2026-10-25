package de.familyplanner.meals.fakes

import de.familyplanner.core.events.CookingConfirmed
import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.plan.Cancellation
import de.familyplanner.meals.plan.CookOutcome
import de.familyplanner.meals.plan.Dish
import de.familyplanner.meals.plan.DishId
import de.familyplanner.meals.plan.DishStatus
import de.familyplanner.meals.plan.MealPlanRepository
import de.familyplanner.meals.plan.ReviewOutcome
import de.familyplanner.meals.plan.SaveOutcome
import java.time.Instant
import java.time.LocalDate

class InMemoryMealPlanRepository : MealPlanRepository {
    private val dishes = mutableMapOf<DishId, Dish>()
    val recordedEvents = mutableListOf<CookingConfirmed>()
    private val cancellations = mutableMapOf<Pair<MemberId, PlanWeek>, Cancellation>()

    override suspend fun findBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<Dish> = dishes.values.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }

    override suspend fun findById(id: DishId): Dish? = dishes[id]

    override suspend fun createIfDayFree(
        dish: Dish,
        at: Instant,
        cookWeek: PlanWeek?,
    ): SaveOutcome {
        if (dishes.values.any { it.date == dish.date }) return SaveOutcome.DAY_TAKEN
        cookWeekOutcome(dish, cookWeek)?.let { return it }
        dishes[dish.id] = dish
        return SaveOutcome.SAVED
    }

    override suspend fun updateIfDayFree(
        dish: Dish,
        at: Instant,
        cookWeek: PlanWeek?,
    ): SaveOutcome {
        if (dish.id !in dishes) return SaveOutcome.NOT_FOUND
        if (dishes.values.any { it.date == dish.date && it.id != dish.id }) return SaveOutcome.DAY_TAKEN
        cookWeekOutcome(dish, cookWeek)?.let { return it }
        dishes[dish.id] = dish
        return SaveOutcome.SAVED
    }

    override suspend fun delete(id: DishId): Boolean = dishes.remove(id) != null

    override suspend fun review(
        id: DishId,
        status: DishStatus,
        reason: String?,
        at: Instant,
    ): ReviewOutcome {
        val dish = dishes[id] ?: return ReviewOutcome.NOT_FOUND
        if (dish.status != DishStatus.SUBMITTED) return ReviewOutcome.NOT_PENDING
        dishes[id] = dish.copy(status = status, rejectionReason = reason)
        return ReviewOutcome.SAVED
    }

    override suspend fun confirmCooked(
        id: DishId,
        by: MemberId,
        today: LocalDate,
        at: Instant,
    ): CookOutcome {
        val dish = dishes[id] ?: return CookOutcome.NOT_FOUND
        val outcome =
            when {
                dish.cookedAt != null -> CookOutcome.ALREADY_COOKED
                dish.status != DishStatus.APPROVED -> CookOutcome.NOT_APPROVED
                dish.date.isAfter(today) -> CookOutcome.TOO_EARLY
                else -> CookOutcome.CONFIRMED
            }
        if (outcome != CookOutcome.CONFIRMED) return outcome
        dishes[id] = dish.copy(cookedAt = at)
        recordedEvents += CookingConfirmed(id.value, dish.cookId.value, dish.name, dish.date.toString(), by.value, at.toEpochMilli())
        return outcome
    }

    override suspend fun findCancellations(week: PlanWeek): List<Cancellation> = cancellations.values.filter { it.week == week }

    override suspend fun cancel(cancellation: Cancellation) {
        cancellations[cancellation.memberId to cancellation.week] = cancellation
        dishes.values
            .filter { it.cookId == cancellation.memberId && PlanWeek.containing(it.date) == cancellation.week }
            .forEach { dishes.remove(it.id) }
    }

    override suspend fun deleteCancellation(
        memberId: MemberId,
        week: PlanWeek,
    ): Boolean = cancellations.remove(memberId to week) != null

    private fun cookWeekOutcome(
        dish: Dish,
        week: PlanWeek?,
    ): SaveOutcome? {
        if (week == null) return null
        val other = dishes.values.any { it.cookId == dish.cookId && it.id != dish.id && PlanWeek.containing(it.date) == week }
        if (other) return SaveOutcome.COOK_WEEK_TAKEN
        return if ((dish.cookId to week) in cancellations) SaveOutcome.COOK_CANCELLED else null
    }
}
