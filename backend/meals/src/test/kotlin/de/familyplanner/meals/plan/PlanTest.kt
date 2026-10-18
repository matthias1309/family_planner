package de.familyplanner.meals.plan

import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.fakes.lines
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanTest {
    private val week = PlanWeek(LocalDate.parse("2026-10-19"))

    private fun dish(date: String) = Dish(DishId("d-$date"), LocalDate.parse(date), MemberId("cook"), "Suppe", lines("Lauch"))

    @Test
    fun `REQ-MEAL-001 week plan has seven days Monday to Sunday`() {
        // Act
        val plan = weekPlanOf(week, emptyList())

        // Assert
        assertEquals((19..25).map { LocalDate.of(2026, 10, it) }, plan.days.map { it.date })
        assertTrue(plan.days.all { it.dish == null })
    }

    @Test
    fun `REQ-MEAL-001 dish appears on its day`() {
        // Arrange
        val wednesday = dish("2026-10-21")

        // Act
        val plan = weekPlanOf(week, listOf(wednesday))

        // Assert
        assertEquals(wednesday, plan.days[2].dish)
        assertEquals(6, plan.days.count { it.dish == null })
    }

    @Test
    fun `REQ-MEAL-001 dishes outside the week are ignored`() {
        // Act
        val plan = weekPlanOf(week, listOf(dish("2026-10-26"), dish("2026-10-18")))

        // Assert
        plan.days.forEach { assertNull(it.dish) }
    }

    @Test
    fun `REQ-MEAL-021 week plan marks dish invalid when cook is absent`() {
        // Act
        val plan = weekPlanOf(week, listOf(dish("2026-10-21"))) { false }

        // Assert
        assertEquals(false, plan.days[2].dayValid)
        assertTrue(plan.days.filter { it.dish == null }.all { it.dayValid })
    }
}
