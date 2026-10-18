package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.attendance.AttendanceDay
import de.familyplanner.meals.attendance.AttendanceSource
import de.familyplanner.meals.attendance.MemberWeekAttendance
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.attendance.WeekAttendance
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChildCookingTest {
    private val week = PlanWeek(LocalDate.parse("2026-10-19"))
    private val child = MemberId("child")
    private val deadline = Instant.parse("2026-10-18T14:00:00Z")

    private fun cook(
        presentDays: Set<Int>,
        submissionDeadline: Instant? = deadline,
    ) = MemberWeekAttendance(
        memberId = child,
        cooks = true,
        days = week.days.mapIndexed { i, d -> AttendanceDay(d, (i + 1) in presentDays, AttendanceSource.PATTERN) },
        submissionDeadline = submissionDeadline,
    )

    @Test
    fun `REQ-MEAL-021 child may cook only on present days`() {
        // Arrange
        val cook = cook(setOf(2, 3, 4))

        // Act / Assert
        requireChildMayCook(LocalDate.parse("2026-10-21"), cook)
        assertFailsWith<AppException.Validation> { requireChildMayCook(LocalDate.parse("2026-10-23"), cook) }
    }

    @Test
    fun `REQ-MEAL-020 child without deadline does not cook`() {
        assertFailsWith<AppException.Validation> { requireChildMayCook(LocalDate.parse("2026-10-21"), cook(setOf(3), null)) }
        assertFailsWith<AppException.Validation> { requireChildMayCook(LocalDate.parse("2026-10-21"), null) }
    }

    @Test
    fun `REQ-MEAL-020 late only after deadline`() {
        assertFalse(isLate(deadline, deadline))
        assertTrue(isLate(deadline, deadline.plusSeconds(1)))
        assertFalse(isLate(null, deadline))
    }

    @Test
    fun `REQ-MEAL-020 children cannot write for past weeks`() {
        // Arrange: Wednesday 2026-10-14 -> current plan week is 2026-10-19
        val clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC)

        // Act / Assert
        assertFailsWith<AppException.Validation> { requireOpenForChildren(PlanWeek(LocalDate.parse("2026-10-12")), clock) }
        requireOpenForChildren(week, clock)
        requireOpenForChildren(week.next(), clock)
    }

    @Test
    fun `REQ-MEAL-021 dish of parent is always day valid`() {
        // Arrange
        val attendance = WeekAttendance(week, deadline, listOf(cook(setOf(2))))
        val parentDish = Dish(DishId("d"), LocalDate.parse("2026-10-21"), MemberId("parent"), "Suppe", emptyList())
        val childDish = parentDish.copy(cookId = child)

        // Act / Assert
        assertTrue(isDayValid(parentDish, attendance))
        assertFalse(isDayValid(childDish, attendance))
        assertTrue(isDayValid(childDish.copy(date = LocalDate.parse("2026-10-20")), attendance))
        assertEquals(true, isDayValid(childDish, attendance.copy(members = emptyList())))
    }
}
