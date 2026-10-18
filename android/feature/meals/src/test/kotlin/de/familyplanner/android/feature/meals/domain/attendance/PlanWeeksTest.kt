package de.familyplanner.android.feature.meals.domain.attendance

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanWeeksTest {
    @Test
    fun `REQ-MEAL-010 week type alternates from reference week`() {
        val reference = LocalDate.of(2026, 10, 5)

        assertEquals(WeekType.A, weekTypeOf(LocalDate.of(2026, 10, 7), reference))
        assertEquals(WeekType.B, weekTypeOf(LocalDate.of(2026, 10, 14), reference))
        assertEquals(WeekType.A, weekTypeOf(LocalDate.of(2026, 10, 21), reference))
    }

    @Test
    fun `REQ-MEAL-010 week type before reference week keeps parity`() {
        val reference = LocalDate.of(2026, 10, 5)

        assertEquals(WeekType.B, weekTypeOf(LocalDate.of(2026, 9, 30), reference))
        assertEquals(WeekType.A, weekTypeOf(LocalDate.of(2026, 9, 21), reference))
    }

    @Test
    fun `REQ-MEAL-010 week type across new year`() {
        val reference = LocalDate.of(2026, 12, 28)

        assertEquals(WeekType.B, weekTypeOf(LocalDate.of(2027, 1, 6), reference))
        assertEquals(WeekType.A, weekTypeOf(LocalDate.of(2027, 1, 12), reference))
    }

    @Test
    fun `REQ-MEAL-010 reference week for this week as B is previous monday`() {
        val today = LocalDate.of(2026, 10, 17)

        assertEquals(LocalDate.of(2026, 10, 12), referenceWeekFor(today, WeekType.A))
        assertEquals(LocalDate.of(2026, 10, 5), referenceWeekFor(today, WeekType.B))
    }

    @Test
    fun `REQ-MEAL-010 reference week round trips to the chosen week type`() {
        val today = LocalDate.of(2026, 10, 17)

        WeekType.entries.forEach { type ->
            assertEquals(type, weekTypeOf(today, referenceWeekFor(today, type)))
        }
    }

    @Test
    fun `REQ-MEAL-012 current plan week is monday after today in Berlin`() {
        assertEquals(LocalDate.of(2026, 10, 19), currentPlanWeek(Instant.parse("2026-10-17T10:00:00Z")))
        // Sonntag 23:30 Berlin (21:30 UTC) gehört noch zur laufenden Woche.
        assertEquals(LocalDate.of(2026, 10, 19), currentPlanWeek(Instant.parse("2026-10-18T21:30:00Z")))
        // Montag 00:30 Berlin (22:30 UTC am Sonntag) ist schon die nächste Woche.
        assertEquals(LocalDate.of(2026, 10, 26), currentPlanWeek(Instant.parse("2026-10-18T22:30:00Z")))
    }

    @Test
    fun `REQ-MEAL-012 current plan week handles the daylight saving change`() {
        // Zeitumstellung am Sonntag, 25.10.2026.
        assertEquals(LocalDate.of(2026, 10, 26), currentPlanWeek(Instant.parse("2026-10-25T10:00:00Z")))
        assertEquals(LocalDate.of(2026, 10, 26), currentPlanWeek(Instant.parse("2026-10-24T23:30:00Z")))
    }

    @Test
    fun `REQ-MEAL-001 current week is monday of today in Berlin`() {
        assertEquals(LocalDate.of(2026, 10, 12), currentWeek(Instant.parse("2026-10-17T10:00:00Z")))
        // Sonntag 23:30 Berlin (21:30 UTC) ist noch diese Woche.
        assertEquals(LocalDate.of(2026, 10, 12), currentWeek(Instant.parse("2026-10-18T21:30:00Z")))
        // Montag 00:30 Berlin (22:30 UTC am Sonntag) ist schon die nächste Woche.
        assertEquals(LocalDate.of(2026, 10, 19), currentWeek(Instant.parse("2026-10-18T22:30:00Z")))
    }

    @Test
    fun `monday of returns the monday of the calendar week`() {
        assertEquals(LocalDate.of(2026, 10, 12), mondayOf(LocalDate.of(2026, 10, 18)))
        assertEquals(LocalDate.of(2026, 10, 12), mondayOf(LocalDate.of(2026, 10, 12)))
    }
}
