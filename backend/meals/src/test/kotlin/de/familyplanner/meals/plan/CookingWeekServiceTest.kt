package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.fakes.PlanFixture
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CookingWeekServiceTest {
    private val fixture = PlanFixture()
    private val service = CookingWeekService(fixture.members, fixture.planRepository, fixture.mealPlan, fixture.attendance, fixture.clock)
    private val week = PlanWeek(LocalDate.parse("2026-10-19"))
    private val tuesday = LocalDate.parse("2026-10-20")

    private suspend fun dishOf(child: Actor) = fixture.mealPlan.create(child, tuesday, child.id, "Spaghetti", listOf("Nudeln"))

    @Test
    fun `REQ-MEAL-023 child cancels own week`() =
        runTest {
            // Arrange
            val child = fixture.child()
            dishOf(child)

            // Act
            val cancellation = service.cancel(child, child.id, week, " krank ")

            // Assert
            assertEquals("krank", cancellation.reason)
            assertNull(
                fixture.mealPlan
                    .week(week)
                    .days[1]
                    .dish,
            )
            assertEquals(
                CookState.CANCELLED,
                service
                    .status(week)
                    .cooks
                    .single()
                    .state,
            )
        }

    @Test
    fun `REQ-MEAL-023 cancellation needs reason`() =
        runTest {
            val child = fixture.child()
            assertFailsWith<AppException.Validation> { service.cancel(child, child.id, week, "   ") }
        }

    @Test
    fun `REQ-MEAL-023 child cannot cancel for sibling`() =
        runTest {
            val child = fixture.child()
            val sibling = fixture.child()
            assertFailsWith<AppException.Forbidden> { service.cancel(child, sibling.id, week, "krank") }
            assertFailsWith<AppException.Forbidden> { service.withdraw(child, sibling.id, week) }
        }

    @Test
    fun `REQ-MEAL-023 parent cancels for child`() =
        runTest {
            val child = fixture.child()
            val parent = fixture.parent()

            service.cancel(parent, child.id, week, "Ausflug")

            assertEquals(
                CookState.CANCELLED,
                service
                    .status(week)
                    .cooks
                    .single()
                    .state,
            )
        }

    @Test
    fun `REQ-MEAL-023 cancelling a non-cooking week fails`() =
        runTest {
            val child = fixture.child(cooks = false)
            assertFailsWith<AppException.Validation> { service.cancel(child, child.id, week, "krank") }
        }

    @Test
    fun `REQ-MEAL-023 cancelling a past week fails`() =
        runTest {
            val child = fixture.child()
            assertFailsWith<AppException.Validation> { service.cancel(child, child.id, week.previous(), "krank") }
        }

    @Test
    fun `REQ-MEAL-023 cancelling a parent fails`() =
        runTest {
            val parent = fixture.parent()
            assertFailsWith<AppException.Validation> { service.cancel(parent, parent.id, week, "krank") }
            assertFailsWith<AppException.NotFound> {
                service.cancel(
                    parent,
                    de.familyplanner.core.member
                        .MemberId("x"),
                    week,
                    "krank",
                )
            }
        }

    @Test
    fun `REQ-MEAL-023 cancelling again replaces reason`() =
        runTest {
            val child = fixture.child()

            service.cancel(child, child.id, week, "krank")
            service.cancel(child, child.id, week, "Ausflug")

            assertEquals(
                "Ausflug",
                service
                    .status(week)
                    .cooks
                    .single()
                    .cancellationReason,
            )
        }

    @Test
    fun `REQ-MEAL-023 withdraw cancellation allows submitting again`() =
        runTest {
            // Arrange
            val child = fixture.child()
            service.cancel(child, child.id, week, "krank")

            // Act
            service.withdraw(child, child.id, week)

            // Assert
            assertEquals(
                CookState.NOT_SUBMITTED,
                service
                    .status(week)
                    .cooks
                    .single()
                    .state,
            )
            dishOf(child)
        }

    @Test
    fun `REQ-MEAL-023 withdraw without cancellation fails`() =
        runTest {
            val child = fixture.child()
            assertFailsWith<AppException.NotFound> { service.withdraw(child, child.id, week) }
        }

    @Test
    fun `REQ-MEAL-033 status combines plan, attendance and cancellations`() =
        runTest {
            // Arrange
            val cooking = fixture.child()
            val cancelled = fixture.child()
            fixture.child(cooks = false)
            dishOf(cooking)
            service.cancel(cancelled, cancelled.id, week, "krank")

            // Act
            val status = service.status(week)

            // Assert
            assertEquals(listOf(CookState.SUBMITTED, CookState.CANCELLED).toSet(), status.cooks.map { it.state }.toSet())
            assertEquals(6, status.openDays.size)
            assertEquals(false, status.readyToOrder)
        }
}
