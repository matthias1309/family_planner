package de.familyplanner.android.feature.meals.domain.plan

import de.familyplanner.android.core.data.ApiResult
import java.time.LocalDate

interface MealPlanRepository {
    suspend fun week(weekStart: LocalDate): ApiResult<WeekPlan>

    suspend fun members(): ApiResult<List<PlanMember>>

    suspend fun createDish(draft: DishDraft): ApiResult<Dish>

    suspend fun updateDish(
        dishId: String,
        draft: DishDraft,
    ): ApiResult<Dish>

    suspend fun deleteDish(dishId: String): ApiResult<Unit>
}
