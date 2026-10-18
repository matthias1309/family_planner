package de.familyplanner.android.feature.meals.data.plan

import de.familyplanner.android.feature.meals.data.mapAllOrNull
import de.familyplanner.android.feature.meals.data.parseDate
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishDraft
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.plan.PlanDay
import de.familyplanner.android.feature.meals.domain.plan.WeekPlan
import de.familyplanner.api.model.SaveDishRequest
import de.familyplanner.api.model.Dish as ApiDish
import de.familyplanner.api.model.DishStatus as ApiDishStatus
import de.familyplanner.api.model.PlanDay as ApiPlanDay
import de.familyplanner.api.model.WeekPlan as ApiWeekPlan

private const val DAYS_PER_WEEK = 7

/** `null` bei einem unbekannten Status (Serverfehler statt Absturz). */
internal fun ApiDishStatus.toDomain(): DishStatus? = DishStatus.entries.firstOrNull { it.name == name }

internal fun ApiDish.toDomain(): Dish? {
    val day = parseDate(date) ?: return null
    val mappedStatus = status.toDomain() ?: return null
    return Dish(id, day, cookId, name, ingredients, mappedStatus, rejectionReason, late, dayValid)
}

internal fun ApiPlanDay.toDomain(): PlanDay? {
    val day = parseDate(date) ?: return null
    val mapped = dish?.let { it.toDomain() ?: return null }
    return PlanDay(day, mapped)
}

/** Weniger oder mehr als sieben Tage sind ein Serverfehler, keine halbe Woche. */
internal fun ApiWeekPlan.toDomain(): WeekPlan? {
    val start = parseDate(weekStart) ?: return null
    val mapped = days.mapAllOrNull { it.toDomain() } ?: return null
    return if (mapped.size == DAYS_PER_WEEK) WeekPlan(start, mapped) else null
}

internal fun DishDraft.toApi() = SaveDishRequest(date.toString(), cookId, name, ingredients)
