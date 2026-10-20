package de.familyplanner.meals.shopping

import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.attendance.PlanWeek
import de.familyplanner.meals.plan.Dish
import de.familyplanner.meals.plan.DishId
import de.familyplanner.meals.plan.DishStatus
import de.familyplanner.meals.plan.Ingredient
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShoppingListTest {
    private val week = PlanWeek(LocalDate.parse("2026-10-19"))
    private val tuesday = LocalDate.parse("2026-10-20")
    private val wednesday = LocalDate.parse("2026-10-21")

    private fun dish(
        id: String,
        date: LocalDate,
        vararg ingredients: Ingredient,
        status: DishStatus = DishStatus.APPROVED,
    ) = Dish(DishId(id), date, MemberId("cook"), "Gericht $id", ingredients.toList(), status)

    private fun item(
        id: String,
        ingredient: Ingredient,
        checked: Boolean = false,
    ) = ShoppingItem(ShoppingItemId(id), week, ingredient, checked)

    private fun listFor(
        dishes: List<Dish> = emptyList(),
        items: List<ShoppingItem> = emptyList(),
        checks: List<ShoppingCheck> = emptyList(),
    ) = shoppingListOf(week, dishes, items, checks)

    @Test
    fun `REQ-MEAL-040 same name and unit are merged with summed quantity and sources`() {
        // Arrange
        val dishes =
            listOf(
                dish("a", tuesday, Ingredient("Spaghetti", 500.0, "g")),
                dish("b", wednesday, Ingredient("spaghetti", 250.0, "g")),
            )

        // Act
        val entries = listFor(dishes).entries

        // Assert
        val entry = entries.single()
        assertEquals("Spaghetti", entry.name)
        assertEquals(750.0, entry.quantity)
        assertEquals("g", entry.unit)
        assertEquals(listOf(DishId("a"), DishId("b")), entry.sources.map { it.dishId })
    }

    @Test
    fun `REQ-MEAL-040 ingredients without quantity appear once per name`() {
        // Arrange
        val dishes = listOf(dish("a", tuesday, Ingredient("Salz")), dish("b", wednesday, Ingredient("Salz")))

        // Act
        val entry = listFor(dishes).entries.single()

        // Assert
        assertEquals(null, entry.quantity)
        assertEquals(2, entry.sources.size)
    }

    @Test
    fun `REQ-MEAL-040 different units stay separate`() {
        // Arrange
        val dishes = listOf(dish("a", tuesday, Ingredient("Sahne", 200.0, "g"), Ingredient("Sahne", 1.0, "Becher")))

        // Act
        val entries = listFor(dishes).entries

        // Assert
        assertEquals(2, entries.size)
    }

    @Test
    fun `REQ-MEAL-040 quantity and no quantity stay separate`() {
        // Arrange
        val dishes = listOf(dish("a", tuesday, Ingredient("Zwiebeln", 2.0), Ingredient("Zwiebeln")))

        // Act
        val entries = listFor(dishes).entries

        // Assert
        assertEquals(2, entries.size)
        assertEquals(listOf<Double?>(null, 2.0), entries.map { it.quantity })
    }

    @Test
    fun `REQ-MEAL-040 submitted and rejected dishes are not included`() {
        // Arrange
        val dishes =
            listOf(
                dish("a", tuesday, Ingredient("Mehl"), status = DishStatus.SUBMITTED),
                dish("b", wednesday, Ingredient("Eier"), status = DishStatus.REJECTED),
                dish("c", LocalDate.parse("2026-10-22"), Ingredient("Milch")),
            )

        // Act
        val entries = listFor(dishes).entries

        // Assert
        assertEquals(listOf("Milch"), entries.map { it.name })
    }

    @Test
    fun `REQ-MEAL-040 dishes outside the week are ignored`() {
        // Arrange
        val dishes = listOf(dish("a", LocalDate.parse("2026-10-26"), Ingredient("Mehl")))

        // Act
        val entries = listFor(dishes).entries

        // Assert
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `REQ-MEAL-040 sums are exact to three decimals`() {
        // Arrange
        val dishes = listOf(dish("a", tuesday, Ingredient("Milch", 0.1, "l")), dish("b", wednesday, Ingredient("Milch", 0.2, "l")))

        // Act
        val entry = listFor(dishes).entries.single()

        // Assert
        assertEquals(0.3, entry.quantity)
    }

    @Test
    fun `REQ-MEAL-040 duplicate ingredient in one dish counts once as source`() {
        // Arrange
        val dishes = listOf(dish("a", tuesday, Ingredient("Käse", 100.0, "g"), Ingredient("Käse", 50.0, "g")))

        // Act
        val entry = listFor(dishes).entries.single()

        // Assert
        assertEquals(150.0, entry.quantity)
        assertEquals(1, entry.sources.size)
    }

    @Test
    fun `REQ-MEAL-040 entries are sorted by name and keep first spelling`() {
        // Arrange
        val dishes = listOf(dish("a", tuesday, Ingredient("Zucchini"), Ingredient("apfel"), Ingredient("Apfel")))

        // Act
        val entries = listFor(dishes).entries

        // Assert
        assertEquals(listOf("apfel", "Zucchini"), entries.map { it.name })
    }

    @Test
    fun `REQ-MEAL-041 free items follow computed entries with item key`() {
        // Arrange
        val dishes = listOf(dish("a", tuesday, Ingredient("Mehl")))
        val items = listOf(item("i1", Ingredient("Zeitung")), item("i2", Ingredient("Batterien", 4.0)))

        // Act
        val entries = listFor(dishes, items).entries

        // Assert
        assertEquals(listOf("Mehl", "Zeitung", "Batterien"), entries.map { it.name })
        assertEquals("item:i1", entries[1].key)
        assertEquals(ShoppingItemId("i1"), entries[1].itemId)
        assertTrue(entries[1].sources.isEmpty())
    }

    @Test
    fun `REQ-MEAL-041 free item keeps its own checked flag`() {
        // Arrange
        val items = listOf(item("i1", Ingredient("Zeitung"), checked = true))

        // Act
        val entry = listFor(items = items).entries.single()

        // Assert
        assertTrue(entry.checked)
    }

    @Test
    fun `REQ-MEAL-041 tick counts only while quantity is unchanged`() {
        // Arrange
        val dishes =
            listOf(dish("a", tuesday, Ingredient("Spaghetti", 500.0, "g")), dish("b", wednesday, Ingredient("Spaghetti", 250.0, "g")))
        val key = "ing:spaghetti|g|q"

        // Act
        val stale = listFor(dishes, checks = listOf(ShoppingCheck(key, 500.0))).entries.single()
        val current = listFor(dishes, checks = listOf(ShoppingCheck(key, 750.0))).entries.single()

        // Assert
        assertEquals(key, current.key)
        assertEquals(false, stale.checked)
        assertEquals(true, current.checked)
    }

    @Test
    fun `REQ-MEAL-041 tick without quantity stays valid`() {
        // Arrange
        val dishes = listOf(dish("a", tuesday, Ingredient("Salz")))

        // Act
        val entry = listFor(dishes, checks = listOf(ShoppingCheck("ing:salz||-", null))).entries.single()

        // Assert
        assertTrue(entry.checked)
    }

    @Test
    fun `REQ-MEAL-040 empty week gives empty list`() {
        // Arrange / Act
        val list = listFor()

        // Assert
        assertTrue(list.entries.isEmpty())
        assertEquals(week, list.week)
    }

    @Test
    fun `REQ-MEAL-040 separator characters in name and unit never make keys collide`() {
        // Arrange
        val dishes = dishesWithSeparators()

        // Act
        val entries = listFor(dishes).entries

        // Assert
        assertEquals(2, entries.size)
        assertEquals(2, entries.map { it.key }.toSet().size)
    }

    private fun dishesWithSeparators() = listOf(dish("a", tuesday, Ingredient("a|g", 1.0), Ingredient("a", 1.0, "g|")))
}
