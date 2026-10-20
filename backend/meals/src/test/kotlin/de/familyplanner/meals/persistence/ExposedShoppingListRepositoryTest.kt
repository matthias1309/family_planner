package de.familyplanner.meals.persistence

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.plan.Ingredient
import de.familyplanner.meals.shopping.ShoppingCheck
import de.familyplanner.meals.shopping.ShoppingItem
import de.familyplanner.meals.shopping.ShoppingItemId
import kotlinx.coroutines.test.runTest
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

class ExposedShoppingListRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-14T10:00:00Z")
    private val week = PlanWeek(LocalDate.parse("2026-10-19"))
    private val otherWeek = PlanWeek(LocalDate.parse("2026-10-26"))
    private lateinit var repository: ExposedShoppingListRepository

    @BeforeTest
    fun setUp() {
        repository = ExposedShoppingListRepository(DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now)))
    }

    private fun item(
        id: String,
        week: PlanWeek = this.week,
        ingredient: Ingredient = Ingredient("Mehl", 500.0, "g"),
    ) = ShoppingItem(ShoppingItemId(id), week, ingredient)

    @Test
    fun `REQ-MEAL-041 items are stored per week in creation order`() =
        runTest {
            // Arrange
            repository.addItemIfBelowLimit(item("b", ingredient = Ingredient("Zeitung")), 50, now)
            repository.addItemIfBelowLimit(item("a", ingredient = Ingredient("Mehl", 500.0, "g")), 50, now.plusSeconds(1))
            repository.addItemIfBelowLimit(item("c", otherWeek), 50, now)

            // Act
            val items = repository.findItems(week)

            // Assert
            assertEquals(listOf("b", "a"), items.map { it.id.value })
            assertEquals(Ingredient("Zeitung", null, null), items[0].ingredient)
            assertEquals(Ingredient("Mehl", 500.0, "g"), items[1].ingredient)
            assertFalse(items[0].checked)
        }

    @Test
    fun `REQ-MEAL-041 item limit is enforced atomically`() =
        runTest {
            // Arrange
            repository.addItemIfBelowLimit(item("1"), 2, now)
            repository.addItemIfBelowLimit(item("2"), 2, now)

            // Act
            val third = repository.addItemIfBelowLimit(item("3"), 2, now)
            val otherWeekItem = repository.addItemIfBelowLimit(item("4", otherWeek), 2, now)

            // Assert
            assertFalse(third)
            assertTrue(otherWeekItem)
            assertEquals(2, repository.findItems(week).size)
        }

    @Test
    fun `REQ-MEAL-041 delete and check only within the item's week`() =
        runTest {
            // Arrange
            repository.addItemIfBelowLimit(item("1"), 50, now)

            // Act / Assert
            assertFalse(repository.setItemChecked(otherWeek, ShoppingItemId("1"), true))
            assertFalse(repository.deleteItem(otherWeek, ShoppingItemId("1")))
            assertTrue(repository.setItemChecked(week, ShoppingItemId("1"), true))
            assertTrue(repository.findItems(week).single().checked)
            assertTrue(repository.deleteItem(week, ShoppingItemId("1")))
            assertFalse(repository.deleteItem(week, ShoppingItemId("1")))
        }

    @Test
    fun `REQ-MEAL-041 checks are saved, replaced and deleted`() =
        runTest {
            // Arrange
            repository.saveCheck(week, ShoppingCheck("ing:salz||-", null), now)
            repository.saveCheck(week, ShoppingCheck("ing:mehl|g|q", 500.0), now)

            // Act
            repository.saveCheck(week, ShoppingCheck("ing:mehl|g|q", 750.0), now)
            val saved = repository.findChecks(week).associate { it.key to it.quantity }
            repository.deleteCheck(week, "ing:salz||-")

            // Assert
            assertEquals(2, saved.size)
            assertEquals(750.0, saved["ing:mehl|g|q"])
            assertNull(saved["ing:salz||-"])
            assertEquals(listOf("ing:mehl|g|q"), repository.findChecks(week).map { it.key })
            assertTrue(repository.findChecks(otherWeek).isEmpty())
        }
}
