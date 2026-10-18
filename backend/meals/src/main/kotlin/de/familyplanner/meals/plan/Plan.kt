package de.familyplanner.meals.plan

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.recipe.RecipeId
import java.time.Instant
import java.time.LocalDate

/** Maximum length of rejection and cancellation reasons. */
const val REASON_MAX = 200

enum class DishStatus { SUBMITTED, APPROVED, REJECTED }

@JvmInline
value class DishId(
    val value: String,
)

/** A dish was copied from a recipe for [servings] portions; the ingredients themselves are stored on the dish. */
data class RecipeRef(
    val recipeId: RecipeId,
    val servings: Int,
)

data class Dish(
    val id: DishId,
    val date: LocalDate,
    val cookId: MemberId,
    val name: String,
    val ingredients: List<Ingredient>,
    val status: DishStatus = DishStatus.APPROVED,
    val rejectionReason: String? = null,
    val late: Boolean = false,
    val recipe: RecipeRef? = null,
)

/** A day of the plan; [dish] is null while the day is still open; [dayValid] is false if the cooking child is absent. */
data class PlanDay(
    val date: LocalDate,
    val dish: Dish?,
    val dayValid: Boolean = true,
)

/** Who performs a write. */
data class Actor(
    val id: MemberId,
    val role: Role,
) {
    val isParent: Boolean get() = role == Role.PARENT
}

data class Cancellation(
    val memberId: MemberId,
    val week: PlanWeek,
    val reason: String,
    val createdAt: Instant,
)

data class WeekPlan(
    val week: PlanWeek,
    val days: List<PlanDay>,
)

/** Result of an atomic write that requires the dish's day (and, for child cooks, the cook's week) to be free. */
enum class SaveOutcome { SAVED, DAY_TAKEN, NOT_FOUND, COOK_WEEK_TAKEN, COOK_CANCELLED }

enum class ReviewOutcome { SAVED, NOT_FOUND, NOT_PENDING }

/** Seven days Monday..Sunday; days without a dish stay open. Dishes outside [week] are ignored. */
fun weekPlanOf(
    week: PlanWeek,
    dishes: List<Dish>,
    isDayValid: (Dish) -> Boolean = { true },
): WeekPlan {
    val byDate = dishes.associateBy { it.date }
    return WeekPlan(week, week.days.map { day -> byDate[day].let { PlanDay(day, it, it == null || isDayValid(it)) } })
}
