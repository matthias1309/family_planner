package de.familyplanner.meals.plan

import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.attendance.MemberWeekAttendance
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.attendance.WeekAttendance
import de.familyplanner.meals.fakes.lines
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekStatusTest {
    private val week = PlanWeek(LocalDate.parse("2026-10-19"))
    private val child = MemberId("child")
    private val parent = MemberId("parent")
    private val deadline = Instant.parse("2026-10-18T14:00:00Z")

    private fun attendance(submissionDeadline: Instant? = deadline) =
        WeekAttendance(week, deadline, listOf(MemberWeekAttendance(child, true, emptyList(), submissionDeadline)))

    private fun dish(
        offset: Long,
        cook: MemberId = parent,
        status: DishStatus = DishStatus.APPROVED,
    ) = Dish(DishId("d$offset"), week.monday.plusDays(offset), cook, "Suppe", lines("Lauch"), status)

    private fun cancellation(reason: String = "krank") = Cancellation(child, week, reason, deadline)

    private fun plan(
        dishes: List<Dish>,
        valid: (Dish) -> Boolean = { true },
    ) = weekPlanOf(week, dishes, valid)

    @Test
    fun `REQ-MEAL-033 cook states`() {
        // Act
        val none = weekStatusOf(plan(emptyList()), attendance(), emptyList()).cooks.single()
        val submitted = weekStatusOf(plan(listOf(dish(1, child, DishStatus.SUBMITTED))), attendance(), emptyList()).cooks.single()
        val approved = weekStatusOf(plan(listOf(dish(1, child))), attendance(), emptyList()).cooks.single()
        val rejected = weekStatusOf(plan(listOf(dish(1, child, DishStatus.REJECTED))), attendance(), emptyList()).cooks.single()
        val cancelled = weekStatusOf(plan(emptyList()), attendance(), listOf(cancellation())).cooks.single()

        // Assert
        assertEquals(CookState.NOT_SUBMITTED, none.state)
        assertEquals(deadline, none.submissionDeadline)
        assertEquals(CookState.SUBMITTED, submitted.state)
        assertEquals(DishId("d1"), submitted.dishId)
        assertEquals(CookState.APPROVED, approved.state)
        assertEquals(CookState.REJECTED, rejected.state)
        assertEquals(CookState.CANCELLED, cancelled.state)
        assertEquals("krank", cancelled.cancellationReason)
    }

    @Test
    fun `REQ-MEAL-033 ready only when all days planned, approved and valid`() {
        val all = (0L..6L).map { dish(it) }

        assertTrue(weekStatusOf(plan(all), attendance(), emptyList()).readyToOrder)
        assertFalse(weekStatusOf(plan(all.dropLast(1)), attendance(), emptyList()).readyToOrder)
        assertFalse(weekStatusOf(plan(all.dropLast(1) + dish(6, child, DishStatus.SUBMITTED)), attendance(), emptyList()).readyToOrder)
        assertFalse(weekStatusOf(plan(all) { it.date != week.days[2] }, attendance(), emptyList()).readyToOrder)
        assertEquals(listOf(week.days[6]), weekStatusOf(plan(all.dropLast(1)), attendance(), emptyList()).openDays)
    }

    @Test
    fun `REQ-MEAL-033 child with dish but no longer cooking is listed`() {
        // Act
        val status = weekStatusOf(plan(listOf(dish(1, child))), attendance(submissionDeadline = null), emptyList())

        // Assert
        assertEquals(listOf(child), status.cooks.map { it.memberId })
    }

    @Test
    fun `REQ-MEAL-033 child that neither cooks nor has a dish is not listed`() {
        assertTrue(weekStatusOf(plan(emptyList()), attendance(submissionDeadline = null), emptyList()).cooks.isEmpty())
    }
}
