package de.familyplanner.meals.web

import de.familyplanner.core.auth.DevicePrincipal
import de.familyplanner.meals.plan.Actor
import de.familyplanner.meals.plan.Dish
import de.familyplanner.meals.plan.PlanDay
import de.familyplanner.meals.plan.WeekPlan
import de.familyplanner.meals.plan.displayText
import de.familyplanner.api.model.Dish as ApiDish
import de.familyplanner.api.model.DishStatus as ApiDishStatus
import de.familyplanner.api.model.PlanDay as ApiPlanDay
import de.familyplanner.api.model.WeekPlan as ApiWeekPlan

fun DevicePrincipal.toActor() = Actor(memberId, role)

fun Dish.toApi(dayValid: Boolean = true) =
    ApiDish(
        id = id.value,
        date = date.toString(),
        cookId = cookId.value,
        name = name,
        ingredients = ingredients.map { it.displayText() },
        structuredIngredients = ingredients.map { it.toApi() },
        recipeId = recipe?.recipeId?.value,
        servings = recipe?.servings,
        status = ApiDishStatus.valueOf(status.name),
        rejectionReason = rejectionReason,
        late = late,
        dayValid = dayValid,
        cookedAt = cookedAt?.toString(),
    )

fun PlanDay.toApi() = ApiPlanDay(date = date.toString(), dish = dish?.toApi(dayValid))

fun WeekPlan.toApi() = ApiWeekPlan(weekStart = week.monday.toString(), days = days.map { it.toApi() })
