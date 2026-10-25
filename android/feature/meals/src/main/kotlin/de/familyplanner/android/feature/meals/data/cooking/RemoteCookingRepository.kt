package de.familyplanner.android.feature.meals.data.cooking

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.core.data.apiCallNoContent
import de.familyplanner.android.feature.meals.domain.cooking.CookingRepository
import de.familyplanner.android.feature.meals.domain.cooking.WeekStatus
import de.familyplanner.api.client.MealsApi
import de.familyplanner.api.model.CancelCookingRequest
import de.familyplanner.api.model.RejectDishRequest
import kotlinx.serialization.json.Json
import java.time.LocalDate

class RemoteCookingRepository(
    private val meals: MealsApi,
    private val json: Json,
) : CookingRepository {
    override suspend fun status(weekStart: LocalDate): ApiResult<WeekStatus> =
        apiCall(json, { meals.getWeekStatus(weekStart.toString()) }) { it.data?.toDomain() }

    override suspend fun approve(dishId: String): ApiResult<Unit> = apiCallNoContent(json) { meals.approveDish(dishId) }

    override suspend fun confirmCooked(dishId: String): ApiResult<Unit> = apiCallNoContent(json) { meals.confirmDishCooked(dishId) }

    override suspend fun reject(
        dishId: String,
        reason: String,
    ): ApiResult<Unit> = apiCallNoContent(json) { meals.rejectDish(dishId, RejectDishRequest(reason)) }

    /** Antwortet mit 200 und Body (nicht 204). */
    override suspend fun cancel(
        weekStart: LocalDate,
        memberId: String,
        reason: String,
    ): ApiResult<Unit> =
        apiCall(json, { meals.cancelCooking(weekStart.toString(), memberId, CancelCookingRequest(reason)) }) { response ->
            response.data?.let { }
        }

    override suspend fun withdraw(
        weekStart: LocalDate,
        memberId: String,
    ): ApiResult<Unit> = apiCallNoContent(json) { meals.withdrawCookingCancellation(weekStart.toString(), memberId) }
}
