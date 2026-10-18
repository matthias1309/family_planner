package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName
import org.slf4j.LoggerFactory
import java.time.Clock

class DishReviewService(
    private val plan: MealPlanRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(DishReviewService::class.java)

    suspend fun approve(id: DishId) {
        requireReviewed(plan.review(id, DishStatus.APPROVED, null, clock.instant()))
        log.info("Dish approved: {}", id.value)
    }

    suspend fun reject(
        id: DishId,
        reason: String,
    ) {
        val validReason = validatedName(reason, "Begründung", REASON_MAX)
        requireReviewed(plan.review(id, DishStatus.REJECTED, validReason, clock.instant()))
        log.info("Dish rejected: {}", id.value)
    }

    private fun requireReviewed(outcome: ReviewOutcome) {
        when (outcome) {
            ReviewOutcome.SAVED -> Unit
            ReviewOutcome.NOT_FOUND -> throw AppException.NotFound("Gericht nicht gefunden")
            ReviewOutcome.NOT_PENDING -> throw AppException.Conflict("Das Gericht wartet nicht auf Freigabe")
        }
    }
}
