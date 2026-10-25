package de.familyplanner.android.feature.meals.domain.cooking

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.feature.meals.domain.plan.MealPlanRepository
import de.familyplanner.android.feature.meals.domain.plan.validateReason
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSync
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

private const val UNKNOWN_NAME = "?"

class LoadWeekStatusUseCase(
    private val cooking: CookingRepository,
    private val plan: MealPlanRepository,
) {
    suspend operator fun invoke(weekStart: LocalDate): ApiResult<StatusOverview> =
        coroutineScope {
            val status = async { cooking.status(weekStart) }
            val members = async { plan.members() }
            val statusResult = status.await()
            val membersResult = members.await()
            if (statusResult !is ApiResult.Success) return@coroutineScope statusResult as ApiResult.Failure
            if (membersResult !is ApiResult.Success) return@coroutineScope membersResult as ApiResult.Failure
            val names = membersResult.data.associate { it.id to it.name }
            val data = statusResult.data
            val cooks = data.cooks.map { CookOverview(it, names[it.memberId] ?: UNKNOWN_NAME) }
            ApiResult.Success(StatusOverview(data.weekStart, data.orderDeadline, data.readyToOrder, data.openDays, cooks))
        }
}

class ReviewDishUseCase(
    private val cooking: CookingRepository,
) {
    suspend fun approve(dishId: String): ApiResult<Unit> = cooking.approve(dishId)

    suspend fun confirmCooked(dishId: String): ApiResult<Unit> = cooking.confirmCooked(dishId)

    suspend fun reject(
        dishId: String,
        reason: String,
    ): ApiResult<Unit> {
        validateReason(reason)?.let { return it }
        return cooking.reject(dishId, reason.trim())
    }
}

class CancelCookingUseCase(
    private val cooking: CookingRepository,
    private val reminders: ReminderSync,
) {
    suspend operator fun invoke(
        weekStart: LocalDate,
        memberId: String,
        reason: String,
    ): ApiResult<Unit> {
        validateReason(reason)?.let { return it }
        return cooking.cancel(weekStart, memberId, reason.trim()).also { if (it is ApiResult.Success) reminders.requestNow() }
    }
}

class WithdrawCancellationUseCase(
    private val cooking: CookingRepository,
    private val reminders: ReminderSync,
) {
    suspend operator fun invoke(
        weekStart: LocalDate,
        memberId: String,
    ): ApiResult<Unit> = cooking.withdraw(weekStart, memberId).also { if (it is ApiResult.Success) reminders.requestNow() }
}

/** Bündelt Status und Absage für den Essensplan (hält die ViewModel-Parameterliste kurz). */
class CookingUseCases(
    val status: LoadWeekStatusUseCase,
    val cancel: CancelCookingUseCase,
    val withdraw: WithdrawCancellationUseCase,
)
