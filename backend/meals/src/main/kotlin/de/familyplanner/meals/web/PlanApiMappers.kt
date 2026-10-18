package de.familyplanner.meals.web

import de.familyplanner.meals.plan.Dish
import de.familyplanner.meals.plan.PlanDay
import de.familyplanner.meals.plan.WeekPlan
import de.familyplanner.api.model.Dish as ApiDish
import de.familyplanner.api.model.PlanDay as ApiPlanDay
import de.familyplanner.api.model.WeekPlan as ApiWeekPlan

fun Dish.toApi() =
    ApiDish(
        id = id.value,
        date = date.toString(),
        cookId = cookId.value,
        name = name,
        ingredients = ingredients,
    )

fun PlanDay.toApi() = ApiPlanDay(date = date.toString(), dish = dish?.toApi())

fun WeekPlan.toApi() = ApiWeekPlan(weekStart = week.monday.toString(), days = days.map { it.toApi() })
