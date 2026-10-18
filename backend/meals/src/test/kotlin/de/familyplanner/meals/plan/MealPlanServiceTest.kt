package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.meals.attendance.AttendancePattern
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.attendance.Presence
import de.familyplanner.meals.fakes.PlanFixture
import de.familyplanner.meals.fakes.lines
import de.familyplanner.meals.recipe.RecipeId
import de.familyplanner.meals.recipe.RecipeInput
import kotlinx.coroutines.test.runTest
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MealPlanServiceTest {
    private val fixture = PlanFixture()
    private val clock = fixture.clock
    private val memberRepository = fixture.members
    private val service = fixture.mealPlan
    private val week = PlanWeek(date("2026-10-19"))
    private val admin = Actor(MemberId("admin"), Role.PARENT)

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
            val dish = service.create(admin, date("2026-10-20"), cook, "Spaghetti", lines("Nudeln", "Tomaten"))

            // Assert
            assertEquals(Dish(DishId("dish-1"), date("2026-10-20"), cook, "Spaghetti", lines("Nudeln", "Tomaten")), dish)
            assertEquals(dish, dishOn("2026-10-20"))
        }

    @Test
    fun `REQ-MEAL-001 week shows open days without dish`() =
        runTest {
            // Arrange
            service.create(admin, date("2026-10-20"), parent(), "Spaghetti", emptyList())

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
            val dish = service.create(admin, date("2026-10-20"), parent(), "  Suppe ", listOf(Ingredient(" Lauch "), Ingredient("Brühe")))

            // Assert
            assertEquals("Suppe", dish.name)
            assertEquals(lines("Lauch", "Brühe"), dish.ingredients)
        }

    @Test
    fun `REQ-MEAL-032 dish without ingredients is allowed`() =
        runTest {
            // Act
            val dish = service.create(admin, date("2026-10-20"), parent(), "Reste", emptyList())

            // Assert
            assertEquals(emptyList(), dish.ingredients)
        }

    @Test
    fun `REQ-MEAL-032 blank name is rejected`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.Validation> { service.create(admin, date("2026-10-20"), cook, "   ", emptyList()) }
        }

    @Test
    fun `REQ-MEAL-032 name longer than 60 characters is rejected`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.Validation> { service.create(admin, date("2026-10-20"), cook, "x".repeat(61), emptyList()) }
        }

    @Test
    fun `REQ-MEAL-032 ingredient longer than 80 characters is rejected`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.Validation> {
                service.create(admin, date("2026-10-20"), cook, "Suppe", lines("x".repeat(81)))
            }
        }

    @Test
    fun `REQ-MEAL-032 more than 30 ingredients are rejected`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.Validation> {
                service.create(admin, date("2026-10-20"), cook, "Suppe", (1..31).map { Ingredient("Zutat $it") })
            }
        }

    @Test
    fun `REQ-MEAL-031 child that does not cook cannot get a dish`() =
        runTest {
            val child = memberRepository.create(aMember(role = Role.CHILD)).id
            assertFailsWith<AppException.Validation> { service.create(admin, date("2026-10-20"), child, "Suppe", emptyList()) }
        }

    @Test
    fun `REQ-MEAL-032 unknown cook is rejected`() =
        runTest {
            assertFailsWith<AppException.NotFound> { service.create(admin, date("2026-10-20"), MemberId("nobody"), "Suppe", emptyList()) }
        }

    @Test
    fun `REQ-MEAL-032 second dish on same day conflicts`() =
        runTest {
            // Arrange
            val cook = parent()
            service.create(admin, date("2026-10-20"), cook, "Spaghetti", emptyList())

            // Act + Assert
            assertFailsWith<AppException.Conflict> { service.create(admin, date("2026-10-20"), cook, "Suppe", emptyList()) }
        }

    @Test
    fun `REQ-MEAL-032 update replaces fields and moves dish`() =
        runTest {
            // Arrange
            val cook = parent()
            val other = parent()
            val dish = service.create(admin, date("2026-10-20"), cook, "Spaghetti", lines("Nudeln"))

            // Act
            val updated = service.update(admin, dish.id, date("2026-10-22"), other, "Lasagne", lines("Platten", "Hack"))

            // Assert
            assertEquals(Dish(dish.id, date("2026-10-22"), other, "Lasagne", lines("Platten", "Hack")), updated)
            assertNull(dishOn("2026-10-20"))
            assertEquals(updated, dishOn("2026-10-22"))
        }

    @Test
    fun `REQ-MEAL-032 update on own day succeeds`() =
        runTest {
            // Arrange
            val cook = parent()
            val dish = service.create(admin, date("2026-10-20"), cook, "Spaghetti", emptyList())

            // Act
            val updated = service.update(admin, dish.id, date("2026-10-20"), cook, "Spaghetti Carbonara", emptyList())

            // Assert
            assertEquals("Spaghetti Carbonara", dishOn("2026-10-20")?.name)
            assertEquals(dish.id, updated.id)
        }

    @Test
    fun `REQ-MEAL-032 moving onto occupied day conflicts`() =
        runTest {
            // Arrange
            val cook = parent()
            val tuesday = service.create(admin, date("2026-10-20"), cook, "Spaghetti", emptyList())
            service.create(admin, date("2026-10-21"), cook, "Suppe", emptyList())

            // Act + Assert
            assertFailsWith<AppException.Conflict> { service.update(admin, tuesday.id, date("2026-10-21"), cook, "Spaghetti", emptyList()) }
            assertEquals("Spaghetti", dishOn("2026-10-20")?.name)
            assertEquals("Suppe", dishOn("2026-10-21")?.name)
        }

    @Test
    fun `REQ-MEAL-032 updating unknown dish fails`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.NotFound> {
                service.update(
                    admin,
                    DishId("missing"),
                    date("2026-10-20"),
                    cook,
                    "Suppe",
                    emptyList(),
                )
            }
        }

    @Test
    fun `REQ-MEAL-032 deleting dish reopens day`() =
        runTest {
            // Arrange
            val dish = service.create(admin, date("2026-10-20"), parent(), "Spaghetti", emptyList())

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

    private suspend fun submit(
        child: Actor,
        day: String = "2026-10-20",
        ingredients: List<Ingredient> = lines("Nudeln"),
    ) = service.create(child, date(day), child.id, "Spaghetti", ingredients)

    @Test
    fun `REQ-MEAL-020 child submits dish and it waits for approval`() =
        runTest {
            // Arrange
            val child = fixture.child()

            // Act
            val dish = submit(child)

            // Assert
            assertEquals(DishStatus.SUBMITTED, dish.status)
            assertEquals(false, dish.late)
            assertEquals(dish, dishOn("2026-10-20"))
        }

    @Test
    fun `REQ-MEAL-020 child dish needs ingredients`() =
        runTest {
            val child = fixture.child()
            assertFailsWith<AppException.Validation> { submit(child, ingredients = emptyList()) }
        }

    @Test
    fun `REQ-MEAL-020 late submission is marked`() =
        runTest {
            // Arrange: Sunday 17:00 Berlin, deadline was 16:00
            val child = fixture.child()
            clock.advance(Duration.ofDays(4).plusHours(5))

            // Act
            val dish = submit(child)

            // Assert
            assertEquals(true, dish.late)
        }

    @Test
    fun `REQ-MEAL-020 child cannot submit for another cook`() =
        runTest {
            val child = fixture.child()
            val sibling = fixture.child()
            val parent = parent()
            assertFailsWith<AppException.Forbidden> { service.create(child, date("2026-10-20"), sibling.id, "Suppe", lines("Lauch")) }
            assertFailsWith<AppException.Forbidden> { service.create(child, date("2026-10-20"), parent, "Suppe", emptyList()) }
        }

    @Test
    fun `REQ-MEAL-020 one dish per child and week`() =
        runTest {
            val child = fixture.child()
            submit(child, "2026-10-20")
            assertFailsWith<AppException.Conflict> { submit(child, "2026-10-21") }
        }

    @Test
    fun `REQ-MEAL-020 submitting is blocked while cancelled`() =
        runTest {
            // Arrange
            val child = fixture.child()
            fixture.planRepository.cancel(Cancellation(child.id, week, "krank", clock.instant()))

            // Act + Assert
            assertFailsWith<AppException.Conflict> { submit(child) }
        }

    @Test
    fun `REQ-MEAL-020 children cannot submit for a past week`() =
        runTest {
            val child = fixture.child()
            assertFailsWith<AppException.Validation> { submit(child, "2026-10-13") }
        }

    @Test
    fun `REQ-MEAL-020 child that does not cook cannot submit`() =
        runTest {
            val child = fixture.child(cooks = false)
            assertFailsWith<AppException.Validation> { submit(child) }
        }

    @Test
    fun `REQ-MEAL-021 child cannot pick an absent day`() =
        runTest {
            // Arrange: present Tuesday to Thursday only
            val pattern = AttendancePattern.Alternating(setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY), emptySet(), week)
            val child = fixture.child(pattern = pattern)

            // Act + Assert
            assertFailsWith<AppException.Validation> { submit(child, "2026-10-23") }
            submit(child, "2026-10-21")
        }

    @Test
    fun `REQ-MEAL-021 week plan flags dish whose day became absent`() =
        runTest {
            // Arrange
            val child = fixture.child()
            submit(child)

            // Act
            fixture.attendance.addException(child.id, date("2026-10-20"), date("2026-10-20"), Presence.ABSENT, null)

            // Assert
            assertEquals(false, service.week(week).days[1].dayValid)
            assertEquals(true, service.week(week).days[2].dayValid)
        }

    @Test
    fun `REQ-MEAL-022 child edit keeps pending`() =
        runTest {
            val child = fixture.child()
            val dish = submit(child)

            val edited = service.update(child, dish.id, dish.date, child.id, "Lasagne", lines("Platten"))

            assertEquals(DishStatus.SUBMITTED, edited.status)
            assertEquals("Lasagne", dishOn("2026-10-20")?.name)
        }

    @Test
    fun `REQ-MEAL-022 approved dish needs approval again after child edit`() =
        runTest {
            // Arrange
            val child = fixture.child()
            val dish = submit(child)
            fixture.planRepository.review(dish.id, DishStatus.APPROVED, null, clock.instant())

            // Act
            val edited = service.update(child, dish.id, dish.date, child.id, "Lasagne", lines("Platten"))

            // Assert
            assertEquals(DishStatus.SUBMITTED, edited.status)
        }

    @Test
    fun `REQ-MEAL-022 rejected dish is resubmitted`() =
        runTest {
            // Arrange
            val child = fixture.child()
            val dish = submit(child)
            fixture.planRepository.review(dish.id, DishStatus.REJECTED, "kein Gemüse", clock.instant())

            // Act
            val edited = service.update(child, dish.id, dish.date, child.id, "Gemüsepfanne", lines("Paprika"))

            // Assert
            assertEquals(DishStatus.SUBMITTED, edited.status)
            assertNull(edited.rejectionReason)
            assertNull(dishOn("2026-10-20")?.rejectionReason)
        }

    @Test
    fun `REQ-MEAL-022 edit after deadline is marked late`() =
        runTest {
            // Arrange
            val child = fixture.child()
            val dish = submit(child)
            clock.advance(Duration.ofDays(4).plusHours(5))

            // Act
            val edited = service.update(child, dish.id, dish.date, child.id, "Lasagne", lines("Platten"))

            // Assert
            assertEquals(true, edited.late)
        }

    @Test
    fun `REQ-MEAL-022 child cannot edit another member's dish or hand dish to another cook`() =
        runTest {
            // Arrange
            val child = fixture.child()
            val sibling = fixture.child()
            val parentDish = service.create(admin, date("2026-10-21"), parent(), "Suppe", emptyList())
            val own = submit(child)

            // Act + Assert
            assertFailsWith<AppException.Forbidden> { service.update(child, parentDish.id, parentDish.date, child.id, "X", lines("y")) }
            assertFailsWith<AppException.Forbidden> { service.update(child, own.id, own.date, sibling.id, "X", lines("y")) }
        }

    @Test
    fun `REQ-MEAL-022 child can move dish within week`() =
        runTest {
            val child = fixture.child()
            val dish = submit(child, "2026-10-20")

            val moved = service.update(child, dish.id, date("2026-10-21"), child.id, "Spaghetti", lines("Nudeln"))

            assertEquals(date("2026-10-21"), moved.date)
        }

    @Test
    fun `REQ-MEAL-031 parent enters child dish approved`() =
        runTest {
            val child = fixture.child()

            val dish = service.create(admin, date("2026-10-20"), child.id, "Spaghetti", lines("Nudeln"))

            assertEquals(DishStatus.APPROVED, dish.status)
            assertEquals(false, dish.late)
        }

    @Test
    fun `REQ-MEAL-031 parent edit of child dish keeps late flag`() =
        runTest {
            // Arrange
            val child = fixture.child()
            clock.advance(Duration.ofDays(4).plusHours(5))
            val dish = submit(child)

            // Act
            val edited = service.update(admin, dish.id, dish.date, child.id, "Lasagne", lines("Platten"))

            // Assert
            assertEquals(DishStatus.APPROVED, edited.status)
            assertEquals(true, edited.late)
        }

    @Test
    fun `REQ-MEAL-031 parent cannot enter dish for non-cooking child`() =
        runTest {
            val child = fixture.child(cooks = false)
            assertFailsWith<AppException.Validation> { service.create(admin, date("2026-10-20"), child.id, "Suppe", lines("Lauch")) }
        }

    private suspend fun aRecipeId(): RecipeId =
        fixture.recipes.create(admin.id, RecipeInput("Carbonara", 4, lines("Nudeln"), emptyList(), null)).id

    @Test
    fun `REQ-MEAL-062 dish keeps structured ingredients and recipe reference`() =
        runTest {
            // Arrange
            val recipeId = aRecipeId()
            val ingredients = listOf(Ingredient("Spaghetti", 625.0, "g"), Ingredient("Salz"))

            // Act
            val dish = service.create(admin, date("2026-10-20"), parent(), "Carbonara", ingredients, RecipeRef(recipeId, 5))

            // Assert
            assertEquals(ingredients, dish.ingredients)
            assertEquals(RecipeRef(recipeId, 5), dish.recipe)
            assertEquals(dish, dishOn("2026-10-20"))
        }

    @Test
    fun `REQ-MEAL-062 unknown recipe is not found`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.NotFound> {
                service.create(admin, date("2026-10-20"), cook, "X", lines("y"), RecipeRef(RecipeId("x"), 4))
            }
        }

    @Test
    fun `REQ-MEAL-062 servings out of range are rejected before recipe lookup`() =
        runTest {
            val cook = parent()
            assertFailsWith<AppException.Validation> {
                service.create(admin, date("2026-10-20"), cook, "X", lines("y"), RecipeRef(RecipeId("x"), 0))
            }
            assertFailsWith<AppException.Validation> {
                service.create(admin, date("2026-10-20"), cook, "X", lines("y"), RecipeRef(RecipeId("x"), 21))
            }
        }

    @Test
    fun `REQ-MEAL-062 child may submit dish from recipe`() =
        runTest {
            // Arrange
            val child = fixture.child()
            val recipeId = aRecipeId()

            // Act
            val dish = service.create(child, date("2026-10-20"), child.id, "Carbonara", lines("Nudeln"), RecipeRef(recipeId, 5))

            // Assert
            assertEquals(DishStatus.SUBMITTED, dish.status)
            assertEquals(RecipeRef(recipeId, 5), dish.recipe)
        }

    @Test
    fun `REQ-MEAL-022 update replaces recipe reference`() =
        runTest {
            // Arrange
            val cook = parent()
            val dish = service.create(admin, date("2026-10-20"), cook, "Carbonara", lines("Nudeln"), RecipeRef(aRecipeId(), 4))

            // Act
            val updated = service.update(admin, dish.id, dish.date, cook, "Carbonara", lines("Nudeln"))

            // Assert
            assertNull(updated.recipe)
            assertNull(dishOn("2026-10-20")?.recipe)
        }
}
