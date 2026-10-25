package de.familyplanner.meals.plan

import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.attendance.PlanWeek
import java.time.Instant
import java.time.LocalDate

interface MealPlanRepository {
    /** Dishes dated within the inclusive range, sorted by date, ingredients in input order. */
    suspend fun findBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<Dish>

    suspend fun findById(id: DishId): Dish?

    /**
     * Atomically inserts unless the day already has a dish. With [cookWeek] (child cooks) it also requires that the cook has
     * no other dish in that week and no cancellation for it. Never returns [SaveOutcome.NOT_FOUND].
     */
    suspend fun createIfDayFree(
        dish: Dish,
        at: Instant,
        cookWeek: PlanWeek? = null,
    ): SaveOutcome

    /** Like [createIfDayFree] for an existing dish; the dish itself does not count as taking its day or week. */
    suspend fun updateIfDayFree(
        dish: Dish,
        at: Instant,
        cookWeek: PlanWeek? = null,
    ): SaveOutcome

    suspend fun delete(id: DishId): Boolean

    /** Atomically sets [status] and [reason] if the dish is SUBMITTED. */
    suspend fun review(
        id: DishId,
        status: DishStatus,
        reason: String?,
        at: Instant,
    ): ReviewOutcome

    /**
     * Atomically marks the dish cooked if it is APPROVED, not cooked yet and dated on or before [today];
     * on success records [de.familyplanner.core.events.CookingConfirmed] in the same transaction.
     */
    suspend fun confirmCooked(
        id: DishId,
        by: MemberId,
        today: LocalDate,
        at: Instant,
    ): CookOutcome

    suspend fun findCancellations(week: PlanWeek): List<Cancellation>

    /** Atomically stores (or replaces) the cancellation and deletes the cook's dishes in that week, ingredients included. */
    suspend fun cancel(cancellation: Cancellation)

    suspend fun deleteCancellation(
        memberId: MemberId,
        week: PlanWeek,
    ): Boolean
}
