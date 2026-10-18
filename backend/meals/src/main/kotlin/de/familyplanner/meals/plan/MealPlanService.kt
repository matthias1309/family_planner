package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import de.familyplanner.meals.attendance.AttendanceService
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.recipe.RecipeRepository
import de.familyplanner.meals.recipe.validatedServings
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

const val DISH_NAME_MAX = 60

/** What a caller wants to store; validated and completed by [MealPlanService]. */
private data class DishInput(
    val id: DishId,
    val date: LocalDate,
    val cookId: MemberId,
    val name: String,
    val ingredients: List<Ingredient>,
    val recipe: RecipeRef?,
)

/** A validated dish plus, for child cooks, the plan week in which they may have only one dish. */
private data class Draft(
    val dish: Dish,
    val cookWeek: PlanWeek?,
)

class MealPlanService(
    private val members: MemberRepository,
    private val plan: MealPlanRepository,
    private val attendance: AttendanceService,
    private val recipes: RecipeRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(MealPlanService::class.java)

    suspend fun week(week: PlanWeek): WeekPlan {
        val weekAttendance = attendance.week(week)
        return weekPlanOf(week, plan.findBetween(week.monday, week.days.last())) { isDayValid(it, weekAttendance) }
    }

    suspend fun create(
        actor: Actor,
        date: LocalDate,
        cookId: MemberId,
        name: String,
        ingredients: List<Ingredient>,
        recipe: RecipeRef? = null,
    ): Dish {
        requireMayWriteFor(actor, cookId)
        val draft = draft(actor, null, DishInput(DishId(newId()), date, cookId, name, ingredients, recipe))
        requireSaved(plan.createIfDayFree(draft.dish, clock.instant(), draft.cookWeek))
        log.info("Dish created: {} {}", draft.dish.id.value, draft.dish.status)
        return draft.dish
    }

    suspend fun update(
        actor: Actor,
        id: DishId,
        date: LocalDate,
        cookId: MemberId,
        name: String,
        ingredients: List<Ingredient>,
        recipe: RecipeRef? = null,
    ): Dish {
        val existing = plan.findById(id) ?: throw AppException.NotFound(DISH_NOT_FOUND)
        requireMayWriteFor(actor, existing.cookId)
        requireMayWriteFor(actor, cookId)
        val draft = draft(actor, existing, DishInput(id, date, cookId, name, ingredients, recipe))
        requireSaved(plan.updateIfDayFree(draft.dish, clock.instant(), draft.cookWeek))
        log.info("Dish updated: {} {}", draft.dish.id.value, draft.dish.status)
        return draft.dish
    }

    suspend fun delete(id: DishId) {
        if (!plan.delete(id)) throw AppException.NotFound(DISH_NOT_FOUND)
        log.info("Dish deleted: {}", id.value)
    }

    /** Children may only write their own dishes. */
    private fun requireMayWriteFor(
        actor: Actor,
        cookId: MemberId,
    ) {
        if (!actor.isParent && cookId != actor.id) throw AppException.Forbidden("Nur für eigene Gerichte")
    }

    private suspend fun draft(
        actor: Actor,
        existing: Dish?,
        input: DishInput,
    ): Draft {
        val name = validatedName(input.name, "Name", DISH_NAME_MAX)
        val ingredients = validatedIngredients(input.ingredients)
        val recipe = validatedRecipeRef(input.recipe)
        val cook = members.findById(input.cookId) ?: throw AppException.NotFound("Mitglied nicht gefunden")
        val dish = Dish(input.id, input.date, input.cookId, name, ingredients, recipe = recipe)
        if (cook.role == Role.PARENT) return Draft(dish, null)
        return childDraft(actor, existing, dish)
    }

    private suspend fun childDraft(
        actor: Actor,
        existing: Dish?,
        dish: Dish,
    ): Draft {
        if (dish.ingredients.isEmpty()) throw AppException.Validation("Bitte mindestens eine Zutat angeben")
        val week = PlanWeek.containing(dish.date)
        if (!actor.isParent) requireOpenForChildren(week, clock)
        val cook = attendance.week(week).members.firstOrNull { it.memberId == dish.cookId }
        requireChildMayCook(dish.date, cook)
        val checked =
            if (actor.isParent) {
                dish.copy(status = DishStatus.APPROVED, late = existing?.takeIf { it.cookId == dish.cookId }?.late ?: false)
            } else {
                dish.copy(status = DishStatus.SUBMITTED, late = isLate(cook?.submissionDeadline, clock.instant()))
            }
        return Draft(checked, week)
    }

    /** Servings are checked before the repository is asked, so out-of-range values fail with 400 rather than 404. */
    private suspend fun validatedRecipeRef(ref: RecipeRef?): RecipeRef? {
        if (ref == null) return null
        validatedServings(ref.servings)
        if (!recipes.exists(ref.recipeId)) throw AppException.NotFound("Rezept nicht gefunden")
        return ref
    }

    private fun requireSaved(outcome: SaveOutcome) {
        val error =
            when (outcome) {
                SaveOutcome.SAVED -> return
                SaveOutcome.DAY_TAKEN -> AppException.Conflict("An diesem Tag ist schon ein Gericht geplant")
                SaveOutcome.NOT_FOUND -> AppException.NotFound(DISH_NOT_FOUND)
                SaveOutcome.COOK_WEEK_TAKEN -> AppException.Conflict("Für diese Woche ist schon ein Gericht eingereicht")
                SaveOutcome.COOK_CANCELLED -> AppException.Conflict("Das Kochen für diese Woche ist abgesagt")
            }
        throw error
    }

    private companion object {
        const val DISH_NOT_FOUND = "Gericht nicht gefunden"
    }
}
