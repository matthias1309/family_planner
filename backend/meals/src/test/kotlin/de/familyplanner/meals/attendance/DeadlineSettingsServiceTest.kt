package de.familyplanner.meals.attendance

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.meals.fakes.InMemoryDeadlineSettingsRepository
import kotlinx.coroutines.test.runTest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeadlineSettingsServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-14T10:00:00Z"))
    private val repository = InMemoryDeadlineSettingsRepository()
    private val service = DeadlineSettingsService(repository, clock)

    private fun week(date: String) = PlanWeek(LocalDate.parse(date))

    @Test
    fun `REQ-MEAL-013 defaults are Sunday 16-00 when nothing is saved`() =
        runTest {
            val overview = service.overview()

            assertEquals(DayOfWeek.SUNDAY, overview.current.orderDeadlineDay)
            assertEquals(LocalTime.of(16, 0), overview.current.orderDeadlineTime)
            assertEquals(LocalTime.of(16, 0), overview.current.childDeadlineTime)
            assertNull(overview.scheduled)
        }

    @Test
    fun `REQ-MEAL-013 changed settings apply from next plan week`() =
        runTest {
            val overview = service.update(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(12, 0))

            assertEquals(DayOfWeek.SUNDAY, overview.current.orderDeadlineDay)
            assertEquals(week("2026-10-26"), overview.scheduled?.effectiveFrom)
            assertEquals(DayOfWeek.SUNDAY, service.effectiveFor(week("2026-10-19")).orderDeadlineDay)
            assertEquals(DayOfWeek.FRIDAY, service.effectiveFor(week("2026-10-26")).orderDeadlineDay)
        }

    @Test
    fun `REQ-MEAL-013 changing twice in same week overwrites scheduled settings`() =
        runTest {
            service.update(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(12, 0))
            val overview = service.update(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(8, 0))

            assertEquals(1, repository.all.size)
            assertEquals(DayOfWeek.SATURDAY, overview.scheduled?.orderDeadlineDay)
        }

    @Test
    fun `REQ-MEAL-013 current plan week is computed in Berlin time`() =
        runTest {
            // Mon 19.10.2026 00:30 Berlin is still Sunday 18.10. 22:30 UTC
            val lateClock = MutableClock(Instant.parse("2026-10-18T22:30:00Z"))

            val overview = DeadlineSettingsService(repository, lateClock).update(DayOfWeek.FRIDAY, LocalTime.NOON, LocalTime.NOON)

            assertEquals(week("2026-11-02"), overview.scheduled?.effectiveFrom)
            assertEquals(week("2026-10-26"), PlanWeek.current(lateClock))
        }
}
