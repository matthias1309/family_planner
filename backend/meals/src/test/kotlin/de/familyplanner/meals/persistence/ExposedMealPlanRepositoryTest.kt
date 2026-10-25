package de.familyplanner.meals.persistence

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.core.persistence.DomainEventsTable
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.fakes.lines
import de.familyplanner.meals.plan.Cancellation
import de.familyplanner.meals.plan.CookOutcome
import de.familyplanner.meals.plan.Dish
import de.familyplanner.meals.plan.DishId
import de.familyplanner.meals.plan.DishStatus
import de.familyplanner.meals.plan.Ingredient
import de.familyplanner.meals.plan.RecipeRef
import de.familyplanner.meals.plan.ReviewOutcome
import de.familyplanner.meals.plan.SaveOutcome
import de.familyplanner.meals.recipe.Recipe
import de.familyplanner.meals.recipe.RecipeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedMealPlanRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-14T10:00:00Z")
    private lateinit var db: Database
    private lateinit var repository: ExposedMealPlanRepository
    private var cookId = MemberId("unset")

    @BeforeTest
    fun setUp() =
        runTest {
            db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))
            cookId = ExposedMemberRepository(db).create(aMember(role = Role.PARENT)).id
            repository = ExposedMealPlanRepository(db)
        }

    private fun dish(
        id: String,
        date: String,
        name: String = "Spaghetti",
        ingredients: List<Ingredient> = lines("Nudeln", "Tomaten", "Basilikum"),
    ) = Dish(DishId(id), LocalDate.parse(date), cookId, name, ingredients)

    private suspend fun all() = repository.findBetween(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))

    private suspend fun ingredientRows() = dbQuery(db) { MealDishIngredientsTable.selectAll().count() }

    @Test
    fun `REQ-MEAL-032 created dish is read back with ingredients in order`() =
        runTest {
            // Arrange
            val dish = dish("d1", "2026-10-20")

            // Act
            val outcome = repository.createIfDayFree(dish, now)

            // Assert
            assertEquals(SaveOutcome.SAVED, outcome)
            assertEquals(listOf(dish), all())
        }

    @Test
    fun `REQ-MEAL-062 structured ingredients and recipe reference roundtrip`() =
        runTest {
            // Arrange
            ExposedRecipeRepository(db).createIfNameFree(
                Recipe(RecipeId("r1"), "Carbonara", 4, lines("Nudeln"), emptyList(), null, cookId),
                now,
            )
            val ingredients = listOf(Ingredient("Spaghetti", 625.0, "g"), Ingredient("Salz"), Ingredient("Lauch", 2.5, "Stange(n)"))
            val dish = dish("d1", "2026-10-20", ingredients = ingredients).copy(recipe = RecipeRef(RecipeId("r1"), 5))

            // Act
            repository.createIfDayFree(dish, now)

            // Assert
            assertEquals(listOf(dish), all())
        }

    @Test
    fun `REQ-MEAL-032 second dish on same day is rejected`() =
        runTest {
            // Arrange
            repository.createIfDayFree(dish("d1", "2026-10-20"), now)

            // Act
            val outcome = repository.createIfDayFree(dish("d2", "2026-10-20", name = "Suppe"), now)

            // Assert
            assertEquals(SaveOutcome.DAY_TAKEN, outcome)
            assertEquals(listOf("Spaghetti"), all().map { it.name })
        }

    @Test
    fun `REQ-MEAL-032 update replaces fields and ingredients and moves dish`() =
        runTest {
            // Arrange
            repository.createIfDayFree(dish("d1", "2026-10-20"), now)
            val changed = dish("d1", "2026-10-22", name = "Suppe", ingredients = lines("Lauch"))

            // Act
            val outcome = repository.updateIfDayFree(changed, now)

            // Assert
            assertEquals(SaveOutcome.SAVED, outcome)
            assertEquals(listOf(changed), all())
            assertEquals(1, ingredientRows())
        }

    @Test
    fun `REQ-MEAL-032 update on own day succeeds`() =
        runTest {
            // Arrange
            repository.createIfDayFree(dish("d1", "2026-10-20"), now)

            // Act
            val outcome = repository.updateIfDayFree(dish("d1", "2026-10-20", name = "Lasagne"), now)

            // Assert
            assertEquals(SaveOutcome.SAVED, outcome)
            assertEquals("Lasagne", all().single().name)
        }

    @Test
    fun `REQ-MEAL-032 moving onto occupied day is rejected`() =
        runTest {
            // Arrange
            repository.createIfDayFree(dish("d1", "2026-10-20"), now)
            repository.createIfDayFree(dish("d2", "2026-10-21", name = "Suppe"), now)

            // Act
            val outcome = repository.updateIfDayFree(dish("d1", "2026-10-21"), now)

            // Assert
            assertEquals(SaveOutcome.DAY_TAKEN, outcome)
            assertEquals(listOf("2026-10-20", "2026-10-21"), all().map { it.date.toString() })
        }

    @Test
    fun `REQ-MEAL-032 updating unknown dish reports not found`() =
        runTest {
            // Act
            val outcome = repository.updateIfDayFree(dish("missing", "2026-10-20"), now)

            // Assert
            assertEquals(SaveOutcome.NOT_FOUND, outcome)
        }

    @Test
    fun `REQ-MEAL-032 delete removes dish and its ingredients`() =
        runTest {
            // Arrange
            repository.createIfDayFree(dish("d1", "2026-10-20"), now)

            // Act
            val deleted = repository.delete(DishId("d1"))
            val again = repository.delete(DishId("d1"))

            // Assert
            assertTrue(deleted)
            assertFalse(again)
            assertTrue(all().isEmpty())
            assertEquals(0, ingredientRows())
        }

    @Test
    fun `REQ-MEAL-001 find between ignores dishes outside the range`() =
        runTest {
            // Arrange
            repository.createIfDayFree(dish("d1", "2026-10-18"), now)
            repository.createIfDayFree(dish("d2", "2026-10-19", ingredients = emptyList()), now)
            repository.createIfDayFree(dish("d3", "2026-10-25"), now)
            repository.createIfDayFree(dish("d4", "2026-10-26"), now)

            // Act
            val week = repository.findBetween(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25"))

            // Assert
            assertEquals(listOf("d2", "d3"), week.map { it.id.value })
            assertEquals(emptyList(), week.first().ingredients)
        }

    private val week = PlanWeek(LocalDate.parse("2026-10-19"))
    private var childId = MemberId("unset")

    private suspend fun child(): MemberId = ExposedMemberRepository(db).create(aMember(role = Role.CHILD)).id.also { childId = it }

    private fun childDish(
        id: String,
        date: String,
        status: DishStatus = DishStatus.SUBMITTED,
    ) = Dish(DishId(id), LocalDate.parse(date), childId, "Pizza", lines("Teig", "Käse"), status, null, late = true)

    @Test
    fun `REQ-MEAL-020 status and late flag are stored and read back`() =
        runTest {
            // Arrange
            child()
            val dish = childDish("c1", "2026-10-20")

            // Act
            repository.createIfDayFree(dish, now, week)

            // Assert
            assertEquals(dish, repository.findById(dish.id))
            assertEquals(listOf(dish), all())
        }

    @Test
    fun `REQ-MEAL-020 second dish of the same cook in a week conflicts but next week is free`() =
        runTest {
            // Arrange
            child()
            repository.createIfDayFree(childDish("c1", "2026-10-20"), now, week)

            // Act
            val sameWeek = repository.createIfDayFree(childDish("c2", "2026-10-21"), now, week)
            val nextWeek = repository.createIfDayFree(childDish("c3", "2026-10-27"), now, week.next())

            // Assert
            assertEquals(SaveOutcome.COOK_WEEK_TAKEN, sameWeek)
            assertEquals(SaveOutcome.SAVED, nextWeek)
        }

    @Test
    fun `REQ-MEAL-022 updating own dish within the week succeeds`() =
        runTest {
            // Arrange
            child()
            repository.createIfDayFree(childDish("c1", "2026-10-20"), now, week)

            // Act
            val outcome = repository.updateIfDayFree(childDish("c1", "2026-10-22"), now, week)

            // Assert
            assertEquals(SaveOutcome.SAVED, outcome)
            assertEquals("2026-10-22", repository.findById(DishId("c1"))?.date.toString())
        }

    @Test
    fun `REQ-MEAL-032 parent dishes are not checked against cook weeks`() =
        runTest {
            repository.createIfDayFree(dish("d1", "2026-10-20"), now)
            assertEquals(SaveOutcome.SAVED, repository.createIfDayFree(dish("d2", "2026-10-21"), now))
        }

    @Test
    fun `REQ-MEAL-023 cancellation blocks creating dishes`() =
        runTest {
            // Arrange
            child()
            repository.cancel(Cancellation(childId, week, "krank", now))

            // Act
            val outcome = repository.createIfDayFree(childDish("c1", "2026-10-20"), now, week)

            // Assert
            assertEquals(SaveOutcome.COOK_CANCELLED, outcome)
        }

    @Test
    fun `REQ-MEAL-023 cancel deletes dish and ingredients of that week only`() =
        runTest {
            // Arrange
            child()
            repository.createIfDayFree(childDish("c1", "2026-10-20"), now, week)
            repository.createIfDayFree(childDish("c2", "2026-10-27"), now, week.next())
            repository.createIfDayFree(dish("p1", "2026-10-21"), now)

            // Act
            repository.cancel(Cancellation(childId, week, "krank", now))

            // Assert
            assertEquals(listOf("p1", "c2"), all().map { it.id.value })
            assertEquals(5, ingredientRows())
        }

    @Test
    fun `REQ-MEAL-023 cancelling twice keeps one entry with the new reason`() =
        runTest {
            // Arrange
            child()
            repository.cancel(Cancellation(childId, week, "krank", now))

            // Act
            repository.cancel(Cancellation(childId, week, "Ausflug", now))

            // Assert
            assertEquals(listOf("Ausflug"), repository.findCancellations(week).map { it.reason })
            assertTrue(repository.findCancellations(week.next()).isEmpty())
        }

    @Test
    fun `REQ-MEAL-023 deleting a cancellation reports whether it existed`() =
        runTest {
            child()
            repository.cancel(Cancellation(childId, week, "krank", now))

            assertTrue(repository.deleteCancellation(childId, week))
            assertFalse(repository.deleteCancellation(childId, week))
        }

    @Test
    fun `REQ-MEAL-030 review only changes pending dishes`() =
        runTest {
            // Arrange
            child()
            repository.createIfDayFree(childDish("c1", "2026-10-20"), now, week)

            // Act
            val rejected = repository.review(DishId("c1"), DishStatus.REJECTED, "kein Gemüse", now)
            val again = repository.review(DishId("c1"), DishStatus.APPROVED, null, now)
            val missing = repository.review(DishId("nope"), DishStatus.APPROVED, null, now)

            // Assert
            assertEquals(ReviewOutcome.SAVED, rejected)
            assertEquals(ReviewOutcome.NOT_PENDING, again)
            assertEquals(ReviewOutcome.NOT_FOUND, missing)
            assertEquals("kein Gemüse", repository.findById(DishId("c1"))?.rejectionReason)
        }

    @Test
    fun `REQ-MEAL-030 find by id returns null for unknown dish`() =
        runTest {
            assertNull(repository.findById(DishId("nope")))
        }

    private suspend fun storedChildDish(
        id: String,
        date: String,
        status: DishStatus = DishStatus.APPROVED,
    ): Dish {
        val child = ExposedMemberRepository(db).create(aMember(role = Role.CHILD)).id
        return dish(id, date).copy(cookId = child, status = status).also { repository.createIfDayFree(it, now) }
    }

    private suspend fun cookingEvents() =
        dbQuery(db) {
            DomainEventsTable.selectAll().where { DomainEventsTable.type eq "cooking.confirmed.v1" }.map { it[DomainEventsTable.payload] }
        }

    @Test
    fun `REQ-MEAL-034 confirming writes cooked_at and the event atomically`() =
        runTest {
            val dish = storedChildDish("d1", "2026-10-14")

            val outcome = repository.confirmCooked(dish.id, cookId, LocalDate.parse("2026-10-14"), now)

            assertEquals(CookOutcome.CONFIRMED, outcome)
            assertEquals(now, repository.findById(dish.id)?.cookedAt)
            val payload = cookingEvents().single()
            assertTrue(payload.contains(dish.cookId.value))
            assertTrue(payload.contains("Spaghetti"))
        }

    @Test
    fun `REQ-MEAL-034 concurrent confirmations record exactly one event`() =
        runTest {
            val dish = storedChildDish("d1", "2026-10-14")
            val today = LocalDate.parse("2026-10-14")

            val outcomes =
                listOf(
                    async(Dispatchers.Default) { repository.confirmCooked(dish.id, cookId, today, now) },
                    async(Dispatchers.Default) { repository.confirmCooked(dish.id, cookId, today, now) },
                ).awaitAll()

            assertEquals(setOf(CookOutcome.CONFIRMED, CookOutcome.ALREADY_COOKED), outcomes.toSet())
            assertEquals(1, cookingEvents().size)
        }

    @Test
    fun `REQ-MEAL-034 outcomes for missing, pending and future dishes`() =
        runTest {
            val pending = storedChildDish("d1", "2026-10-13", DishStatus.SUBMITTED)
            val future = storedChildDish("d2", "2026-10-15")
            val today = LocalDate.parse("2026-10-14")

            assertEquals(CookOutcome.NOT_FOUND, repository.confirmCooked(DishId("x"), cookId, today, now))
            assertEquals(CookOutcome.NOT_APPROVED, repository.confirmCooked(pending.id, cookId, today, now))
            assertEquals(CookOutcome.TOO_EARLY, repository.confirmCooked(future.id, cookId, today, now))
            assertEquals(0, cookingEvents().size)
        }
}
