package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import de.familyplanner.meals.attendance.AttendanceService
import de.familyplanner.meals.attendance.PlanWeek
import org.slf4j.LoggerFactory
import java.time.Clock

class CookingWeekService(
    private val members: MemberRepository,
    private val plan: MealPlanRepository,
    private val mealPlan: MealPlanService,
    private val attendance: AttendanceService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(CookingWeekService::class.java)

    suspend fun cancel(
        actor: Actor,
        memberId: MemberId,
        week: PlanWeek,
        reason: String,
    ): Cancellation {
        requireChildTarget(actor, memberId)
        requireOpenForChildren(week, clock)
        val cooks =
            attendance
                .week(week)
                .members
                .firstOrNull { it.memberId == memberId }
                ?.submissionDeadline != null
        if (!cooks) throw AppException.Validation("Das Kind kocht in dieser Woche nicht")
        val cancellation = Cancellation(memberId, week, validatedName(reason, "Begründung", REASON_MAX), clock.instant())
        plan.cancel(cancellation)
        log.info("Cooking cancelled: {} {}", memberId.value, week.monday)
        return cancellation
    }

    suspend fun withdraw(
        actor: Actor,
        memberId: MemberId,
        week: PlanWeek,
    ) {
        requireChildTarget(actor, memberId)
        if (!plan.deleteCancellation(memberId, week)) throw AppException.NotFound("Keine Absage vorhanden")
        log.info("Cooking cancellation withdrawn: {} {}", memberId.value, week.monday)
    }

    suspend fun status(week: PlanWeek): WeekStatus = weekStatusOf(mealPlan.week(week), attendance.week(week), plan.findCancellations(week))

    private suspend fun requireChildTarget(
        actor: Actor,
        memberId: MemberId,
    ) {
        requireMayActFor(actor, memberId)
        val target = members.findById(memberId) ?: throw AppException.NotFound("Mitglied nicht gefunden")
        if (target.role != Role.CHILD) throw AppException.Validation("Nur Kinder können das Kochen absagen")
    }

    private fun requireMayActFor(
        actor: Actor,
        memberId: MemberId,
    ) {
        if (!actor.isParent && memberId != actor.id) throw AppException.Forbidden("Nur für das eigene Kochen")
    }
}
