package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.core.events.EventNotifier
import de.familyplanner.meals.fakes.PlanFixture
import de.familyplanner.meals.fakes.lines
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CookingConfirmationServiceTest {
    // Wednesday 2026-10-14, 12:00 in Europe/Berlin
    private val fixture = PlanFixture(Instant.parse("2026-10-14T10:00:00Z"))
    private var dispatches = 0
    private val notifier = EventNotifier { dispatches++ }
    private val service = CookingConfirmationService(fixture.members, fixture.planRepository, notifier, fixture.clock)

    /** Seeds the repository directly: children cannot submit for the current or past weeks through the service. */
    private suspend fun seedChildDish(
        date: String,
        status: DishStatus,
        name: String = "Spaghetti",
    ): Pair<Actor, Dish> {
        val child = fixture.child()
        val dish = Dish(DishId("dish-$date-$status"), LocalDate.parse(date), child.id, name, lines("Nudeln"), status)
        fixture.planRepository.createIfDayFree(dish, fixture.clock.instant())
        return child to dish
    }

    private suspend fun approvedChildDish(date: String) = seedChildDish(date, DishStatus.APPROVED)

    @Test
    fun `REQ-MEAL-034 parent confirms approved child dish on its day`() =
        runTest {
            val parent = fixture.parent()
            val (child, dish) = approvedChildDish("2026-10-14")

            service.confirm(dish.id, parent.id)

            assertNotNull(fixture.planRepository.findById(dish.id)?.cookedAt)
            val event = fixture.planRepository.recordedEvents.single()
            assertEquals(child.id.value, event.memberId)
            assertEquals("Spaghetti", event.dishName)
            assertEquals(parent.id.value, event.confirmedBy)
            assertEquals(1, dispatches)
        }

    @Test
    fun `REQ-MEAL-034 confirming after the cooking day is allowed`() =
        runTest {
            val (_, dish) = approvedChildDish("2026-10-13")

            service.confirm(dish.id, fixture.parent().id)

            assertEquals(1, fixture.planRepository.recordedEvents.size)
        }

    @Test
    fun `REQ-MEAL-034 confirming before the cooking day is a conflict`() =
        runTest {
            val (_, dish) = approvedChildDish("2026-10-15")

            assertFailsWith<AppException.Conflict> { service.confirm(dish.id, fixture.parent().id) }

            assertEquals(0, fixture.planRepository.recordedEvents.size)
            assertEquals(0, dispatches)
        }

    @Test
    fun `REQ-MEAL-034 submitted or rejected dish cannot be confirmed`() =
        runTest {
            val parent = fixture.parent()
            val (_, submitted) = seedChildDish("2026-10-13", DishStatus.SUBMITTED)
            val (_, rejected) = seedChildDish("2026-10-12", DishStatus.REJECTED)

            assertFailsWith<AppException.Conflict> { service.confirm(submitted.id, parent.id) }
            assertFailsWith<AppException.Conflict> { service.confirm(rejected.id, parent.id) }
            assertEquals(0, fixture.planRepository.recordedEvents.size)
        }

    @Test
    fun `REQ-MEAL-034 parent dish cannot be confirmed`() =
        runTest {
            val parent = fixture.parent()
            val dish = fixture.mealPlan.create(parent, LocalDate.parse("2026-10-13"), parent.id, "Suppe", emptyList())

            assertFailsWith<AppException.Validation> { service.confirm(dish.id, parent.id) }
        }

    @Test
    fun `REQ-MEAL-034 second confirmation is a conflict`() =
        runTest {
            val parent = fixture.parent()
            val (_, dish) = approvedChildDish("2026-10-14")
            service.confirm(dish.id, parent.id)

            assertFailsWith<AppException.Conflict> { service.confirm(dish.id, parent.id) }

            assertEquals(1, fixture.planRepository.recordedEvents.size)
        }

    @Test
    fun `REQ-MEAL-034 unknown dish is not found`() =
        runTest {
            assertFailsWith<AppException.NotFound> { service.confirm(DishId("missing"), fixture.parent().id) }
            assertNull(fixture.planRepository.findById(DishId("missing")))
        }

    @Test
    fun `REQ-MEAL-034 today is taken in Europe-Berlin`() =
        runTest {
            // 23:30 UTC on the 13th is already the 14th in Berlin (CEST)
            fixture.clock.advance(java.time.Duration.between(Instant.parse("2026-10-14T10:00:00Z"), Instant.parse("2026-10-13T23:30:00Z")))
            val (_, dish) = approvedChildDish("2026-10-14")

            service.confirm(dish.id, fixture.parent().id)

            assertEquals(1, fixture.planRepository.recordedEvents.size)
        }
}
