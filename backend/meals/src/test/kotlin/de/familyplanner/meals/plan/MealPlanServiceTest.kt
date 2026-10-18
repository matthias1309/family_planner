package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.core.fakes.InMemoryMemberRepository
import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.fakes.InMemoryMealPlanRepository
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MealPlanServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-14T10:00:00Z"))
    private val memberRepository = InMemoryMemberRepository()
    private var nextId = 0
    private val service = MealPlanService(memberRepository, InMemoryMealPlanRepository(), clock) { "dish-${++nextId}" }
    private val week = PlanWeek(date("2026-10-19"))

    private fun date(value: String) = LocalDate.parse(value)

    private suspend fun parent(): MemberId = memberRepository.create(aMember(role = Role.PARENT)).id

    private suspend fun dishOn(day: String) =
        service
            .week(week)
            .days
            .single { it.date == date(day) }
            .dish

    @Test
    fun `REQ-MEAL-032 parent plans dish on open day`() =
        runTest {
            // Arrange
            val cook = parent()

            // Act
            val dish = service.create(date("2026-10-20"), cook, "Spaghetti", listOf("Nudeln", "Tomaten"))

            // Assert
            assertEquals(Dish(DishId("dish-1"), date("2026-10-20"), cook, "Spaghetti", listOf("Nudeln", "Tomaten")), dish)
            assertEquals(dish, dishOn("2026-10-20"))
        }

    @Test
    fun `REQ-MEAL-001 week shows open days without dish`() =
        runTest {
            // Arrange
            service.create(date("2026-10-20"), parent(), "Spaghetti", emptyList())

            // Act
            val plan = service.week(week)

            // Assert
            assertEquals(7, plan.days.size)
            assertEquals(6, plan.days.count { it.dish == null })
        }

    @Test
    fun `REQ-MEAL-032 name and ingredients are trimmed and blank lines removed`() =
        runTest {
            // Act
            val dish = service.create(date("2026-10-20"), parent(), "  Suppe ", listOf(" Lauch ", "", "   ", "Brühe"))

            // Assert
            assertEquals("Suppe", dish.name)
            assertEquals(listOf("Lauch", "Brühe"), dish.ingredients)
        }

    @Test
    fun `REQ-MEAL-032 dish without ingredients is allowed`() =
        runTest {
            // Act
            val dish = service.create(date("2026-10-20"), parent(), "Reste", emptyList())

            // Assert
            assertEquals(emptyList(), dish.ingredients)
        }

    @Test
    fun `REQ-MEAL-032 blank name is rejected`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.Validation> { service.create(date("2026-10-20"), cook, "   ", emptyList()) }
        }

    @Test
    fun `REQ-MEAL-032 name longer than 60 characters is rejected`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.Validation> { service.create(date("2026-10-20"), cook, "x".repeat(61), emptyList()) }
        }

    @Test
    fun `REQ-MEAL-032 ingredient longer than 80 characters is rejected`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.Validation> {
                service.create(date("2026-10-20"), cook, "Suppe", listOf("x".repeat(81)))
            }
        }

    @Test
    fun `REQ-MEAL-032 more than 30 ingredients are rejected`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.Validation> {
                service.create(date("2026-10-20"), cook, "Suppe", (1..31).map { "Zutat $it" })
            }
        }

    @Test
    fun `REQ-MEAL-032 more than 100 raw ingredient lines are rejected even if blank`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.Validation> {
                service.create(date("2026-10-20"), cook, "Suppe", List(101) { "" })
            }
        }

    @Test
    fun `REQ-MEAL-032 30 ingredients plus blank lines are accepted`() =
        runTest {
            // Act
            val dish = service.create(date("2026-10-20"), parent(), "Suppe", (1..30).map { "Zutat $it" } + List(5) { "" })

            // Assert
            assertEquals(30, dish.ingredients.size)
        }

    @Test
    fun `REQ-MEAL-032 cook must be a parent`() =
        runTest {
            val child = memberRepository.create(aMember(role = Role.CHILD)).id
            assertFailsWith<AppException.Validation> { service.create(date("2026-10-20"), child, "Suppe", emptyList()) }
        }

    @Test
    fun `REQ-MEAL-032 unknown cook is rejected`() =
        runTest {
            assertFailsWith<AppException.NotFound> { service.create(date("2026-10-20"), MemberId("nobody"), "Suppe", emptyList()) }
        }

    @Test
    fun `REQ-MEAL-032 second dish on same day conflicts`() =
        runTest {
            // Arrange
            val cook = parent()
            service.create(date("2026-10-20"), cook, "Spaghetti", emptyList())

            // Act + Assert
            assertFailsWith<AppException.Conflict> { service.create(date("2026-10-20"), cook, "Suppe", emptyList()) }
        }

    @Test
    fun `REQ-MEAL-032 update replaces fields and moves dish`() =
        runTest {
            // Arrange
            val cook = parent()
            val other = parent()
            val dish = service.create(date("2026-10-20"), cook, "Spaghetti", listOf("Nudeln"))

            // Act
            val updated = service.update(dish.id, date("2026-10-22"), other, "Lasagne", listOf("Platten", "Hack"))

            // Assert
            assertEquals(Dish(dish.id, date("2026-10-22"), other, "Lasagne", listOf("Platten", "Hack")), updated)
            assertNull(dishOn("2026-10-20"))
            assertEquals(updated, dishOn("2026-10-22"))
        }

    @Test
    fun `REQ-MEAL-032 update on own day succeeds`() =
        runTest {
            // Arrange
            val cook = parent()
            val dish = service.create(date("2026-10-20"), cook, "Spaghetti", emptyList())

            // Act
            val updated = service.update(dish.id, date("2026-10-20"), cook, "Spaghetti Carbonara", emptyList())

            // Assert
            assertEquals("Spaghetti Carbonara", dishOn("2026-10-20")?.name)
            assertEquals(dish.id, updated.id)
        }

    @Test
    fun `REQ-MEAL-032 moving onto occupied day conflicts`() =
        runTest {
            // Arrange
            val cook = parent()
            val tuesday = service.create(date("2026-10-20"), cook, "Spaghetti", emptyList())
            service.create(date("2026-10-21"), cook, "Suppe", emptyList())

            // Act + Assert
            assertFailsWith<AppException.Conflict> { service.update(tuesday.id, date("2026-10-21"), cook, "Spaghetti", emptyList()) }
            assertEquals("Spaghetti", dishOn("2026-10-20")?.name)
            assertEquals("Suppe", dishOn("2026-10-21")?.name)
        }

    @Test
    fun `REQ-MEAL-032 updating unknown dish fails`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.NotFound> { service.update(DishId("missing"), date("2026-10-20"), cook, "Suppe", emptyList()) }
        }

    @Test
    fun `REQ-MEAL-032 deleting dish reopens day`() =
        runTest {
            // Arrange
            val dish = service.create(date("2026-10-20"), parent(), "Spaghetti", emptyList())

            // Act
            service.delete(dish.id)

            // Assert
            assertNull(dishOn("2026-10-20"))
        }

    @Test
    fun `REQ-MEAL-032 deleting unknown dish fails`() =
        runTest {
            assertFailsWith<AppException.NotFound> { service.delete(DishId("missing")) }
        }
}
