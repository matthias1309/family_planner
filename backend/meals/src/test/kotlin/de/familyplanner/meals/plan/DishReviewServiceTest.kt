package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.meals.fakes.PlanFixture
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DishReviewServiceTest {
    private val fixture = PlanFixture()
    private val service = DishReviewService(fixture.planRepository, fixture.clock)
    private val date = LocalDate.parse("2026-10-20")

    private suspend fun submittedDish(): Dish {
        val child = fixture.child()
        return fixture.mealPlan.create(child, date, child.id, "Spaghetti", listOf("Nudeln"))
    }

    private suspend fun stored(id: DishId) = fixture.planRepository.findById(id)

    @Test
    fun `REQ-MEAL-030 approve submitted dish`() =
        runTest {
            val dish = submittedDish()

            service.approve(dish.id)

            assertEquals(DishStatus.APPROVED, stored(dish.id)?.status)
        }

    @Test
    fun `REQ-MEAL-030 reject stores trimmed reason`() =
        runTest {
            val dish = submittedDish()

            service.reject(dish.id, " kein Gemüse ")

            assertEquals(DishStatus.REJECTED, stored(dish.id)?.status)
            assertEquals("kein Gemüse", stored(dish.id)?.rejectionReason)
        }

    @Test
    fun `REQ-MEAL-030 reject needs reason`() =
        runTest {
            val dish = submittedDish()

            assertFailsWith<AppException.Validation> { service.reject(dish.id, "  ") }
            assertFailsWith<AppException.Validation> { service.reject(dish.id, "x".repeat(REASON_MAX + 1)) }
            service.reject(dish.id, "x".repeat(REASON_MAX))
        }

    @Test
    fun `REQ-MEAL-030 only pending dishes can be reviewed`() =
        runTest {
            // Arrange
            val approved = submittedDish().also { service.approve(it.id) }
            val parent = fixture.parent()
            val parentDish = fixture.mealPlan.create(parent, date.plusDays(1), parent.id, "Suppe", emptyList())
            val rejected = fixture.child().let { fixture.mealPlan.create(it, date.plusDays(2), it.id, "Pizza", listOf("Teig")) }
            service.reject(rejected.id, "nein")

            // Act + Assert
            assertFailsWith<AppException.Conflict> { service.approve(approved.id) }
            assertFailsWith<AppException.Conflict> { service.approve(parentDish.id) }
            assertFailsWith<AppException.Conflict> { service.reject(rejected.id, "nochmal") }
        }

    @Test
    fun `REQ-MEAL-030 reviewing unknown dish fails`() =
        runTest {
            assertFailsWith<AppException.NotFound> { service.approve(DishId("missing")) }
            assertFailsWith<AppException.NotFound> { service.reject(DishId("missing"), "nein") }
        }
}
