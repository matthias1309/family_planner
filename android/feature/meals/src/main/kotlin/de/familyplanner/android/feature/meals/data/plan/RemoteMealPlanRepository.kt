package de.familyplanner.android.feature.meals.data.plan

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.core.data.apiCallNoContent
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishDraft
import de.familyplanner.android.feature.meals.domain.plan.MealPlanRepository
import de.familyplanner.android.feature.meals.domain.plan.PlanMember
import de.familyplanner.android.feature.meals.domain.plan.WeekPlan
import de.familyplanner.api.client.MealsApi
import de.familyplanner.api.client.MembersApi
import kotlinx.serialization.json.Json
import java.time.LocalDate
import de.familyplanner.api.model.Role as ApiRole

class RemoteMealPlanRepository(
    private val meals: MealsApi,
    private val memberApi: MembersApi,
    private val json: Json,
) : MealPlanRepository {
    override suspend fun week(weekStart: LocalDate): ApiResult<WeekPlan> =
        apiCall(json, { meals.getWeekPlan(weekStart.toString()) }) { it.data?.toDomain() }

    override suspend fun members(): ApiResult<List<PlanMember>> =
        apiCall(json, { memberApi.listMembers() }) { response ->
            response.data?.map { PlanMember(it.id, it.name, it.role == ApiRole.PARENT) }
        }

    override suspend fun createDish(draft: DishDraft): ApiResult<Dish> =
        apiCall(json, {
            meals.createDish(draft.toApi())
        }) { it.data?.toDomain() }

    override suspend fun updateDish(
        dishId: String,
        draft: DishDraft,
    ): ApiResult<Dish> = apiCall(json, { meals.updateDish(dishId, draft.toApi()) }) { it.data?.toDomain() }

    override suspend fun deleteDish(dishId: String): ApiResult<Unit> = apiCallNoContent(json) { meals.deleteDish(dishId) }
}
