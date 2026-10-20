package de.familyplanner.meals.shopping

import de.familyplanner.core.error.AppException
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.fakes.PlanFixture
import de.familyplanner.meals.plan.Ingredient
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShoppingListServiceTest {
    private val fixture = PlanFixture()
    private val service = fixture.shopping
    private val week = PlanWeek(LocalDate.parse("2026-10-19"))
    private val tuesday = LocalDate.parse("2026-10-20")
    private val wednesday = LocalDate.parse("2026-10-21")

    private suspend fun planDish(
        date: LocalDate,
        vararg ingredients: Ingredient,
    ) {
        val parent = fixture.parent()
        fixture.mealPlan.create(parent, date, parent.id, "Gericht", ingredients.toList())
    }

    @Test
    fun `REQ-MEAL-040 list contains approved dishes of the week`() =
        runTest {
            // Arrange
            planDish(tuesday, Ingredient("Mehl", 500.0, "g"))

            // Act
            val list = service.list(week)

            // Assert
            assertEquals(listOf("Mehl"), list.entries.map { it.name })
        }

    @Test
    fun `REQ-MEAL-041 added item appears in the list`() =
        runTest {
            // Act
            val list = service.addItem(week, Ingredient(" Zeitung "))

            // Assert
            val entry = list.entries.single()
            assertEquals("Zeitung", entry.name)
            assertNotNull(entry.itemId)
        }

    @Test
    fun `REQ-MEAL-041 item with blank name is rejected`() =
        runTest {
            assertFailsWith<AppException.Validation> { service.addItem(week, Ingredient("  ")) }
        }

    @Test
    fun `REQ-MEAL-041 more than 50 items are rejected`() =
        runTest {
            // Arrange
            repeat(SHOPPING_ITEMS_MAX_COUNT) { service.addItem(week, Ingredient("Artikel $it")) }

            // Act / Assert
            assertFailsWith<AppException.Validation> { service.addItem(week, Ingredient("Einer zu viel")) }
        }

    @Test
    fun `REQ-MEAL-041 deleting unknown or foreign-week item fails with not found`() =
        runTest {
            // Arrange
            val itemId =
                service
                    .addItem(week, Ingredient("Zeitung"))
                    .entries
                    .single()
                    .itemId!!

            // Act / Assert
            assertFailsWith<AppException.NotFound> { service.deleteItem(week, ShoppingItemId("unknown")) }
            assertFailsWith<AppException.NotFound> { service.deleteItem(week.next(), itemId) }
            service.deleteItem(week, itemId)
            assertTrue(service.list(week).entries.isEmpty())
        }

    @Test
    fun `REQ-MEAL-041 checking a computed entry marks it checked`() =
        runTest {
            // Arrange
            planDish(tuesday, Ingredient("Mehl", 500.0, "g"))
            val key =
                service
                    .list(week)
                    .entries
                    .single()
                    .key

            // Act
            val list = service.setChecked(week, key, true)

            // Assert
            assertTrue(list.entries.single().checked)
        }

    @Test
    fun `REQ-MEAL-041 checked entry reopens when another dish adds quantity`() =
        runTest {
            // Arrange
            planDish(tuesday, Ingredient("Mehl", 500.0, "g"))
            service.setChecked(
                week,
                service
                    .list(week)
                    .entries
                    .single()
                    .key,
                true,
            )

            // Act
            planDish(wednesday, Ingredient("Mehl", 250.0, "g"))
            val entry = service.list(week).entries.single()

            // Assert
            assertEquals(750.0, entry.quantity)
            assertFalse(entry.checked)
        }

    @Test
    fun `REQ-MEAL-041 unchecking removes the tick`() =
        runTest {
            // Arrange
            planDish(tuesday, Ingredient("Salz"))
            val key =
                service
                    .list(week)
                    .entries
                    .single()
                    .key
            service.setChecked(week, key, true)

            // Act
            val list = service.setChecked(week, key, false)

            // Assert
            assertFalse(list.entries.single().checked)
            assertTrue(fixture.shoppingRepository.findChecks(week).isEmpty())
        }

    @Test
    fun `REQ-MEAL-041 checking a free item`() =
        runTest {
            // Arrange
            val key =
                service
                    .addItem(week, Ingredient("Zeitung"))
                    .entries
                    .single()
                    .key

            // Act
            val list = service.setChecked(week, key, true)

            // Assert
            assertTrue(list.entries.single().checked)
        }

    @Test
    fun `REQ-MEAL-041 unknown key fails with not found`() =
        runTest {
            assertFailsWith<AppException.NotFound> { service.setChecked(week, "ing:nope||-", true) }
        }

    @Test
    fun `REQ-MEAL-041 blank or too long key fails with validation`() =
        runTest {
            assertFailsWith<AppException.Validation> { service.setChecked(week, " ", true) }
            assertFailsWith<AppException.Validation> { service.setChecked(week, "k".repeat(ENTRY_KEY_MAX + 1), true) }
        }
}
