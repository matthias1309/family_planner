package de.familyplanner.meals.persistence

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.meals.plan.Dish
import de.familyplanner.meals.plan.DishId
import de.familyplanner.meals.plan.SaveOutcome
import kotlinx.coroutines.test.runTest
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
        ingredients: List<String> = listOf("Nudeln", "Tomaten", "Basilikum"),
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
            val changed = dish("d1", "2026-10-22", name = "Suppe", ingredients = listOf("Lauch"))

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
}
