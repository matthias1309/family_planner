package de.familyplanner.meals.fakes

import de.familyplanner.core.fakes.InMemoryMemberRepository
import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.meals.attendance.AttendancePattern
import de.familyplanner.meals.attendance.AttendanceProfile
import de.familyplanner.meals.attendance.AttendanceService
import de.familyplanner.meals.attendance.DeadlineSettingsService
import de.familyplanner.meals.plan.Actor
import de.familyplanner.meals.plan.MealPlanService
import java.time.Instant

/** Wires the in-memory fakes that the meal plan services share in unit tests. */
class PlanFixture(
    start: Instant = Instant.parse("2026-10-14T10:00:00Z"),
) {
    val clock = MutableClock(start)
    val members = InMemoryMemberRepository()
    val planRepository = InMemoryMealPlanRepository()
    val attendanceRepository = InMemoryAttendanceRepository()
    val attendance =
        AttendanceService(members, attendanceRepository, DeadlineSettingsService(InMemoryDeadlineSettingsRepository(), clock), clock)
    private var nextId = 0
    val mealPlan = MealPlanService(members, planRepository, attendance, clock) { "dish-${++nextId}" }

    suspend fun parent(): Actor = Actor(members.create(aMember(role = Role.PARENT)).id, Role.PARENT)

    /** A child that always attends; [cooks] controls the profile flag. */
    suspend fun child(
        cooks: Boolean = true,
        pattern: AttendancePattern = AttendancePattern.Always,
    ): Actor {
        val id: MemberId = members.create(aMember(role = Role.CHILD)).id
        attendanceRepository.saveProfile(AttendanceProfile(id, cooks, pattern), clock.instant())
        return Actor(id, Role.CHILD)
    }
}
