package de.familyplanner.meals.attendance

import de.familyplanner.core.member.MemberId
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceCalculatorTest {
    private val memberId = MemberId("child-1")
    private val referenceWeek = PlanWeek(LocalDate.parse("2026-10-19"))
    private val tuesdayToThursday = setOf(TUESDAY, WEDNESDAY, THURSDAY)
    private val tuesdayToSunday = setOf(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)
    private val alternating = AttendancePattern.Alternating(tuesdayToThursday, tuesdayToSunday, referenceWeek)

    private fun profile(pattern: AttendancePattern) = AttendanceProfile(memberId, cooks = true, pattern = pattern)

    private fun exception(
        from: String,
        to: String,
        presence: Presence,
    ) = AttendanceException(
        ExceptionId("e-$from"),
        memberId,
        LocalDate.parse(from),
        LocalDate.parse(to),
        presence,
        null,
        Instant.parse("2026-10-01T00:00:00Z"),
    )

    private fun presentDays(days: List<AttendanceDay>): Set<DayOfWeek> = days.filter { it.present }.map { it.date.dayOfWeek }.toSet()

    private fun week(date: String) = PlanWeek(LocalDate.parse(date))

    @Test
    fun `REQ-MEAL-010 always pattern is present every day`() {
        val days = attendanceFor(week("2026-10-19"), profile(AttendancePattern.Always), emptyList())

        assertEquals(7, days.size)
        assertEquals(true, days.all { it.present && it.source == AttendanceSource.PATTERN })
    }

    @Test
    fun `REQ-MEAL-010 alternating pattern uses week A in reference week`() {
        val days = attendanceFor(week("2026-10-19"), profile(alternating), emptyList())

        assertEquals(tuesdayToThursday, presentDays(days))
    }

    @Test
    fun `REQ-MEAL-010 alternating pattern uses week B in following week`() {
        val days = attendanceFor(week("2026-10-26"), profile(alternating), emptyList())

        assertEquals(tuesdayToSunday, presentDays(days))
        assertEquals(false, days.first { it.date.dayOfWeek == MONDAY }.present)
    }

    @Test
    fun `REQ-MEAL-010 parity works before reference week and across years`() {
        val pattern = alternating

        assertEquals(tuesdayToSunday, presentDays(attendanceFor(week("2026-10-12"), profile(pattern), emptyList())))
        assertEquals(tuesdayToThursday, presentDays(attendanceFor(week("2026-10-05"), profile(pattern), emptyList())))
        // 2026-10-19 + 10 weeks = 2026-12-28 (A), +11 weeks = 2027-01-04 (B)
        assertEquals(tuesdayToThursday, presentDays(attendanceFor(week("2026-12-28"), profile(pattern), emptyList())))
        assertEquals(tuesdayToSunday, presentDays(attendanceFor(week("2027-01-04"), profile(pattern), emptyList())))
    }

    @Test
    fun `REQ-MEAL-011 present exception overrides absent pattern day`() {
        val exceptions = listOf(exception("2026-10-19", "2026-10-19", Presence.PRESENT))

        val monday = attendanceFor(week("2026-10-19"), profile(alternating), exceptions).first()

        assertEquals(AttendanceDay(LocalDate.parse("2026-10-19"), true, AttendanceSource.EXCEPTION), monday)
    }

    @Test
    fun `REQ-MEAL-011 absent exception overrides present pattern day`() {
        val exceptions = listOf(exception("2026-10-21", "2026-10-23", Presence.ABSENT))

        val days = attendanceFor(week("2026-10-19"), profile(alternating), exceptions)

        assertEquals(setOf(TUESDAY), presentDays(days))
        assertEquals(AttendanceSource.EXCEPTION, days.first { it.date.dayOfWeek == WEDNESDAY }.source)
    }

    @Test
    fun `REQ-MEAL-011 exception boundaries are inclusive`() {
        val exceptions = listOf(exception("2026-10-22", "2026-10-22", Presence.ABSENT))

        val days = attendanceFor(week("2026-10-19"), profile(AttendancePattern.Always), exceptions)

        assertEquals(listOf(THURSDAY), days.filter { !it.present }.map { it.date.dayOfWeek })
        assertEquals(1, days.count { it.source == AttendanceSource.EXCEPTION })
    }

    @Test
    fun `REQ-MEAL-011 exceptions overlap detection treats touching days as overlap`() {
        val first = exception("2026-10-01", "2026-10-05", Presence.ABSENT)

        assertEquals(true, first.overlaps(exception("2026-10-05", "2026-10-07", Presence.PRESENT)))
        assertEquals(false, first.overlaps(exception("2026-10-06", "2026-10-07", Presence.PRESENT)))
    }

    @Test
    fun `PlanWeek rejects non-Monday`() {
        val error = kotlin.runCatching { PlanWeek(LocalDate.parse("2026-10-20")) }.exceptionOrNull()

        assertEquals(true, error is de.familyplanner.core.error.AppException.Validation)
    }
}
