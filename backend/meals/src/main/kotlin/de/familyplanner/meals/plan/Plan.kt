package de.familyplanner.meals.plan

import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.attendance.PlanWeek
import java.time.LocalDate

@JvmInline
value class DishId(
    val value: String,
)

data class Dish(
    val id: DishId,
    val date: LocalDate,
    val cookId: MemberId,
    val name: String,
    val ingredients: List<String>,
)

/** A day of the plan; [dish] is null while the day is still open. */
data class PlanDay(
    val date: LocalDate,
    val dish: Dish?,
)

data class WeekPlan(
    val week: PlanWeek,
    val days: List<PlanDay>,
)

/** Result of an atomic write that requires the dish's day to be free. */
enum class SaveOutcome { SAVED, DAY_TAKEN, NOT_FOUND }

/** Seven days Monday..Sunday; days without a dish stay open. Dishes outside [week] are ignored. */
fun weekPlanOf(
    week: PlanWeek,
    dishes: List<Dish>,
): WeekPlan {
    val byDate = dishes.associateBy { it.date }
    return WeekPlan(week, week.days.map { PlanDay(it, byDate[it]) })
}
