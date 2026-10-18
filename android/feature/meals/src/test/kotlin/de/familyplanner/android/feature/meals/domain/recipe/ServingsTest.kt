package de.familyplanner.android.feature.meals.domain.recipe

import kotlin.test.Test
import kotlin.test.assertEquals

class ServingsTest {
    @Test
    fun `REQ-MEAL-062 four servings scaled to five`() {
        val scaled =
            scaleIngredients(
                listOf(Ingredient("Vollkornspaghetti", 500.0, "g"), Ingredient("Lauch", 2.0, "Stange(n)")),
                base = 4,
                target = 5,
            )
        assertEquals(listOf(Ingredient("Vollkornspaghetti", 625.0, "g"), Ingredient("Lauch", 2.5, "Stange(n)")), scaled)
    }

    @Test
    fun `REQ-MEAL-062 ingredient without quantity stays without`() {
        assertEquals(listOf(Ingredient("Salz")), scaleIngredients(listOf(Ingredient("Salz")), 4, 8))
    }

    @Test
    fun `REQ-MEAL-062 scaled quantity is rounded to three decimals`() {
        assertEquals(listOf(Ingredient("Brot", 0.333, "Stück")), scaleIngredients(listOf(Ingredient("Brot", 1.0, "Stück")), 3, 1))
    }

    @Test
    fun `REQ-MEAL-062 default servings is present members clamped`() {
        assertEquals(1, defaultServings(0))
        assertEquals(5, defaultServings(5))
        assertEquals(20, defaultServings(25))
    }

    @Test
    fun `REQ-MEAL-062 scaled quantity stays within the parsable range`() {
        val scaled =
            scaleIngredients(listOf(Ingredient("Mehl", 9000.0, "g"), Ingredient("Salz", 0.001, "g")), base = 1, target = 20) +
                scaleIngredients(listOf(Ingredient("Salz", 0.001, "g")), base = 20, target = 1)

        assertEquals(listOf(10_000.0, 0.02, 0.001), scaled.map { it.quantity })
        scaled.forEach { assertEquals(it, parseIngredientLine(it.displayText())) }
    }
}
