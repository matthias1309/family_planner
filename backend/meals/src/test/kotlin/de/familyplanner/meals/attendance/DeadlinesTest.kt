package de.familyplanner.meals.attendance

import de.familyplanner.core.member.MemberId
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeadlinesTest {
    private val memberId = MemberId("child-1")
    private val referenceWeek = PlanWeek(LocalDate.parse("2026-10-19"))
    private val alternating =
        AttendancePattern.Alternating(
            weekA = setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
            weekB = DayOfWeek.entries.drop(1).toSet(),
            referenceWeek = referenceWeek,
        )
    private val defaults = DeadlineSettings.defaults(PlanWeek(LocalDate.parse("2026-01-05")))

    private fun profile(
        pattern: AttendancePattern = alternating,
        cooks: Boolean = true,
    ) = AttendanceProfile(memberId, cooks, pattern)

    private fun absent(
        from: String,
        to: String,
    ) = AttendanceException(
        ExceptionId("e"),
        memberId,
        LocalDate.parse(from),
        LocalDate.parse(to),
        Presence.ABSENT,
        null,
        Instant.parse("2026-10-01T00:00:00Z"),
    )

    private fun week(date: String) = PlanWeek(LocalDate.parse(date))

    @Test
    fun `REQ-MEAL-012 deadline is Thursday 16-00 when last present day is Thursday`() {
        // plan week 26.10. -> running week 19.-25.10. is type A (Tue-Thu); summer time until 25.10. 03:00
        val deadline = submissionDeadline(week("2026-10-26"), defaults, profile(), emptyList())

        assertEquals(Instant.parse("2026-10-22T14:00:00Z"), deadline)
    }

    @Test
    fun `REQ-MEAL-012 deadline equals order deadline when present until Sunday`() {
        // running week 26.10.-01.11. is type B (Tue-Sun); winter time since 25.10.
        val deadline = submissionDeadline(week("2026-11-02"), defaults, profile(), emptyList())

        assertEquals(Instant.parse("2026-11-01T15:00:00Z"), deadline)
        assertEquals(orderDeadline(week("2026-11-02"), defaults), deadline)
    }

    @Test
    fun `REQ-MEAL-012 permanent child deadline equals order deadline`() {
        val deadline = submissionDeadline(week("2026-10-26"), defaults, profile(AttendancePattern.Always), emptyList())

        assertEquals(orderDeadline(week("2026-10-26"), defaults), deadline)
    }

    @Test
    fun `REQ-MEAL-012 deadline never later than order deadline`() {
        val settings = defaults.copy(orderDeadlineDay = DayOfWeek.FRIDAY, orderDeadlineTime = LocalTime.NOON)

        val deadline = submissionDeadline(week("2026-11-02"), settings, profile(), emptyList())

        // Friday 30.10. 12:00 CET = 11:00Z, earlier than the child's Sunday 16:00
        assertEquals(Instant.parse("2026-10-30T11:00:00Z"), deadline)
    }

    @Test
    fun `REQ-MEAL-012 child absent all running week falls back to order deadline`() {
        val exceptions = listOf(absent("2026-10-19", "2026-10-25"))

        val deadline = submissionDeadline(week("2026-10-26"), defaults, profile(), exceptions)

        assertEquals(orderDeadline(week("2026-10-26"), defaults), deadline)
    }

    @Test
    fun `REQ-MEAL-012 child absent all plan week has no deadline`() {
        val exceptions = listOf(absent("2026-10-26", "2026-11-01"))

        assertNull(submissionDeadline(week("2026-10-26"), defaults, profile(), exceptions))
    }

    @Test
    fun `REQ-MEAL-012 non-cooking child has no deadline`() {
        assertNull(submissionDeadline(week("2026-10-26"), defaults, profile(cooks = false), emptyList()))
    }

    @Test
    fun `REQ-MEAL-012 deadline respects daylight saving change`() {
        // Sunday 25.10.2026 16:00 is already winter time (CET) -> 15:00Z; Sunday 29.03.2026 16:00 is summer time -> 14:00Z
        assertEquals(Instant.parse("2026-10-25T15:00:00Z"), orderDeadline(week("2026-10-26"), defaults))
        assertEquals(Instant.parse("2026-03-29T14:00:00Z"), orderDeadline(week("2026-03-30"), defaults))
        assertEquals(Instant.parse("2026-10-18T14:00:00Z"), orderDeadline(week("2026-10-19"), defaults))
    }

    @Test
    fun `REQ-MEAL-013 defaults are Sunday 16-00`() {
        assertEquals(DayOfWeek.SUNDAY, defaults.orderDeadlineDay)
        assertEquals(LocalTime.of(16, 0), defaults.orderDeadlineTime)
        assertEquals(LocalTime.of(16, 0), defaults.childDeadlineTime)
    }
}
