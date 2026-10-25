package de.familyplanner.android.feature.meals.fakes

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.cooking.CookStatus
import de.familyplanner.android.feature.meals.domain.cooking.CookingRepository
import de.familyplanner.android.feature.meals.domain.cooking.WeekStatus
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.plan.PlanMember
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.time.LocalDate

/** Do 15.10., 16:00 Berlin. */
val benDeadline: Instant = Instant.parse("2026-10-15T14:00:00Z")
val childLea = PlanMember("c-2", "Lea", false)

/** Bens eingereichtes Gericht in der Planwoche (Di 20.10.). */
val childDish = Dish("d-2", LocalDate.of(2026, 10, 20), "c-1", "Curry", listOf("Reis"), DishStatus.SUBMITTED)

fun cookStatus(
    memberId: String = "c-1",
    state: CookState = CookState.NOT_SUBMITTED,
    deadline: Instant? = benDeadline,
    dishId: String? = null,
    late: Boolean = false,
    dayValid: Boolean = true,
    cancellationReason: String? = null,
) = CookStatus(memberId, deadline, state, dishId, late, dayValid, cancellationReason)

fun statusOf(
    weekStart: LocalDate = planWeek,
    vararg cooks: CookStatus,
    openDays: List<LocalDate> = emptyList(),
    ready: Boolean = false,
) = WeekStatus(weekStart, Instant.parse("2026-10-25T15:00:00Z"), ready, openDays, cooks.toList())

class FakeCookingRepository : CookingRepository {
    var statusResult: (LocalDate) -> ApiResult<WeekStatus> = { ApiResult.Success(statusOf(it, cookStatus())) }
    var approveResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var confirmCookedResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var rejectResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var cancelResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var withdrawResult: ApiResult<Unit> = ApiResult.Success(Unit)

    val requestedWeeks = mutableListOf<LocalDate>()
    val approved = mutableListOf<String>()
    val confirmedCooked = mutableListOf<String>()
    val rejected = mutableListOf<Pair<String, String>>()
    val cancelled = mutableListOf<Triple<LocalDate, String, String>>()
    val withdrawn = mutableListOf<Pair<LocalDate, String>>()

    /** Hält die Statusantwort für eine Woche zurück (Wochenwechsel-Rennen). */
    val statusGates = mutableMapOf<LocalDate, CompletableDeferred<Unit>>()

    /** Hält Freigabe bzw. Absage zurück (Doppelklick-Tests). */
    var approveGate: CompletableDeferred<Unit>? = null
    var cancelGate: CompletableDeferred<Unit>? = null

    override suspend fun status(weekStart: LocalDate): ApiResult<WeekStatus> {
        requestedWeeks += weekStart
        statusGates[weekStart]?.await()
        return statusResult(weekStart)
    }

    override suspend fun approve(dishId: String): ApiResult<Unit> {
        approved += dishId
        approveGate?.await()
        return approveResult
    }

    override suspend fun confirmCooked(dishId: String): ApiResult<Unit> {
        confirmedCooked += dishId
        return confirmCookedResult
    }

    override suspend fun reject(
        dishId: String,
        reason: String,
    ): ApiResult<Unit> {
        rejected += dishId to reason
        return rejectResult
    }

    override suspend fun cancel(
        weekStart: LocalDate,
        memberId: String,
        reason: String,
    ): ApiResult<Unit> {
        cancelled += Triple(weekStart, memberId, reason)
        cancelGate?.await()
        return cancelResult
    }

    override suspend fun withdraw(
        weekStart: LocalDate,
        memberId: String,
    ): ApiResult<Unit> {
        withdrawn += weekStart to memberId
        return withdrawResult
    }
}
