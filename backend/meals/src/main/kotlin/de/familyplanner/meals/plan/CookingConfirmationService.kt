package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.core.events.EventNotifier
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import de.familyplanner.meals.attendance.ZONE
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate

/** A parent confirms that a child cooked its approved dish (REQ-MEAL-034). */
class CookingConfirmationService(
    private val members: MemberRepository,
    private val plan: MealPlanRepository,
    private val events: EventNotifier,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(CookingConfirmationService::class.java)

    suspend fun confirm(
        id: DishId,
        by: MemberId,
    ) {
        val dish = plan.findById(id) ?: throw AppException.NotFound("Gericht nicht gefunden")
        if (members.findById(dish.cookId)?.role != Role.CHILD) {
            throw AppException.Validation("Nur Kindergerichte können als gekocht bestätigt werden")
        }
        val now = clock.instant()
        requireConfirmed(plan.confirmCooked(id, by, LocalDate.ofInstant(now, ZONE), now))
        log.info("Dish cooked: {}", id.value)
        events.dispatchPending()
    }

    private fun requireConfirmed(outcome: CookOutcome) {
        failureOf(outcome)?.let { throw it }
    }

    private fun failureOf(outcome: CookOutcome): AppException? =
        when (outcome) {
            CookOutcome.CONFIRMED -> null
            CookOutcome.NOT_FOUND -> AppException.NotFound("Gericht nicht gefunden")
            CookOutcome.NOT_APPROVED -> AppException.Conflict("Das Gericht ist nicht freigegeben")
            CookOutcome.TOO_EARLY -> AppException.Conflict("Das Gericht kann erst am Kochtag bestätigt werden")
            CookOutcome.ALREADY_COOKED -> AppException.Conflict("Das Gericht ist schon als gekocht bestätigt")
        }
}
