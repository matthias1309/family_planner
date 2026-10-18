package de.familyplanner.android.feature.meals.domain.cooking

import java.time.Instant
import java.time.LocalDate

enum class CookState { NOT_SUBMITTED, SUBMITTED, APPROVED, REJECTED, CANCELLED }

data class CookStatus(
    val memberId: String,
    val submissionDeadline: Instant?,
    val state: CookState,
    val dishId: String?,
    val late: Boolean,
    val dayValid: Boolean,
    val cancellationReason: String?,
)

data class WeekStatus(
    val weekStart: LocalDate,
    val orderDeadline: Instant,
    val readyToOrder: Boolean,
    val openDays: List<LocalDate>,
    val cooks: List<CookStatus>,
)

/** Koch mit aufgelöstem Namen („?“, wenn unbekannt). */
data class CookOverview(
    val status: CookStatus,
    val name: String,
)

data class StatusOverview(
    val weekStart: LocalDate,
    val orderDeadline: Instant,
    val readyToOrder: Boolean,
    val openDays: List<LocalDate>,
    val cooks: List<CookOverview>,
)
