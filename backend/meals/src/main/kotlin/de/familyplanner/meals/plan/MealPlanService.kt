package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import de.familyplanner.meals.attendance.PlanWeek
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

const val DISH_NAME_MAX = 60
const val INGREDIENT_MAX = 80
const val INGREDIENTS_MAX_COUNT = 30

/** Bounds the raw input, blank lines included, before any per-line work. */
const val INGREDIENT_LINES_RAW_MAX = 100

class MealPlanService(
    private val members: MemberRepository,
    private val plan: MealPlanRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(MealPlanService::class.java)

    suspend fun week(week: PlanWeek): WeekPlan = weekPlanOf(week, plan.findBetween(week.monday, week.days.last()))

    suspend fun create(
        date: LocalDate,
        cookId: MemberId,
        name: String,
        ingredients: List<String>,
    ): Dish {
        val dish = validated(DishId(newId()), date, cookId, name, ingredients)
        requireSaved(plan.createIfDayFree(dish, clock.instant()))
        log.info("Dish created: {}", dish.id.value)
        return dish
    }

    suspend fun update(
        id: DishId,
        date: LocalDate,
        cookId: MemberId,
        name: String,
        ingredients: List<String>,
    ): Dish {
        val dish = validated(id, date, cookId, name, ingredients)
        requireSaved(plan.updateIfDayFree(dish, clock.instant()))
        log.info("Dish updated: {}", dish.id.value)
        return dish
    }

    suspend fun delete(id: DishId) {
        if (!plan.delete(id)) throw AppException.NotFound(DISH_NOT_FOUND)
        log.info("Dish deleted: {}", id.value)
    }

    private suspend fun validated(
        id: DishId,
        date: LocalDate,
        cookId: MemberId,
        name: String,
        ingredients: List<String>,
    ): Dish {
        val validName = validatedName(name, "Name", DISH_NAME_MAX)
        val validIngredients = validatedIngredients(ingredients)
        requireParentCook(cookId)
        return Dish(id, date, cookId, validName, validIngredients)
    }

    private suspend fun requireParentCook(cookId: MemberId) {
        val cook = members.findById(cookId) ?: throw AppException.NotFound("Mitglied nicht gefunden")
        if (cook.role != Role.PARENT) throw AppException.Validation("Koch/Köchin muss ein Elternteil sein")
    }

    private fun validatedIngredients(lines: List<String>): List<String> {
        requireRawLineLimit(lines)
        val ingredients = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (ingredients.any { it.length > INGREDIENT_MAX }) {
            throw AppException.Validation("Eine Zutat darf höchstens $INGREDIENT_MAX Zeichen lang sein")
        }
        if (ingredients.size > INGREDIENTS_MAX_COUNT) {
            throw AppException.Validation(TOO_MANY_INGREDIENTS)
        }
        return ingredients
    }

    private fun requireRawLineLimit(lines: List<String>) {
        if (lines.size > INGREDIENT_LINES_RAW_MAX) throw AppException.Validation(TOO_MANY_INGREDIENTS)
    }

    private fun requireSaved(outcome: SaveOutcome) {
        when (outcome) {
            SaveOutcome.SAVED -> Unit
            SaveOutcome.DAY_TAKEN -> throw AppException.Conflict("An diesem Tag ist schon ein Gericht geplant")
            SaveOutcome.NOT_FOUND -> throw AppException.NotFound(DISH_NOT_FOUND)
        }
    }

    private companion object {
        const val DISH_NOT_FOUND = "Gericht nicht gefunden"
        const val TOO_MANY_INGREDIENTS = "Höchstens $INGREDIENTS_MAX_COUNT Zutaten je Gericht"
    }
}
