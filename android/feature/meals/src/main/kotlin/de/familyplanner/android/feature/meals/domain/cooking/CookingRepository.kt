package de.familyplanner.android.feature.meals.domain.cooking

import de.familyplanner.android.core.data.ApiResult
import java.time.LocalDate

interface CookingRepository {
    suspend fun status(weekStart: LocalDate): ApiResult<WeekStatus>

    suspend fun approve(dishId: String): ApiResult<Unit>

    suspend fun reject(
        dishId: String,
        reason: String,
    ): ApiResult<Unit>

    suspend fun cancel(
        weekStart: LocalDate,
        memberId: String,
        reason: String,
    ): ApiResult<Unit>

    suspend fun withdraw(
        weekStart: LocalDate,
        memberId: String,
    ): ApiResult<Unit>
}
