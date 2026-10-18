package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IngredientTest {
    @Test
    fun `REQ-MEAL-062 display text formats quantity unit and name`() {
        // Arrange
        val cases =
            listOf(
                Ingredient("Vollkornspaghetti", 500.0, "g") to "500 g Vollkornspaghetti",
                Ingredient("Lauch", 2.5, "Stange(n)") to "2,5 Stange(n) Lauch",
                Ingredient("Salz") to "Salz",
                Ingredient("Tortilla-Wraps", 4.0) to "4 Tortilla-Wraps",
                Ingredient("Mehl", 10000.0, "g") to "10000 g Mehl",
            )

        // Act + Assert
        cases.forEach { (ingredient, expected) -> assertEquals(expected, ingredient.displayText()) }
    }

    @Test
    fun `REQ-MEAL-062 lines become name-only ingredients`() {
        assertEquals(listOf(Ingredient("Nudeln"), Ingredient("Salz")), ingredientsFromLines(listOf("  Nudeln ", "", "Salz")))
    }

    @Test
    fun `REQ-MEAL-020 more than 100 raw lines are rejected`() {
        assertFailsWith<AppException.Validation> { ingredientsFromLines(List(101) { "" }) }
    }

    @Test
    fun `REQ-MEAL-032 30 ingredients plus blank lines are accepted`() {
        // Act
        val result = validatedIngredients(ingredientsFromLines((1..30).map { "Zutat $it" } + List(5) { "" }))

        // Assert
        assertEquals(30, result.size)
    }

    @Test
    fun `REQ-MEAL-062 ingredient is trimmed and blank unit becomes null`() {
        assertEquals(listOf(Ingredient("Lauch", 2.0, null)), validatedIngredients(listOf(Ingredient(" Lauch ", 2.0, "  "))))
    }

    @Test
    fun `REQ-MEAL-062 invalid quantity is rejected`() {
        listOf(0.0, -1.0, 10000.1, Double.NaN, Double.POSITIVE_INFINITY).forEach { quantity ->
            assertFailsWith<AppException.Validation>("quantity $quantity") { validatedIngredients(listOf(Ingredient("Mehl", quantity))) }
        }
    }

    @Test
    fun `REQ-MEAL-062 too long name or unit is rejected`() {
        assertFailsWith<AppException.Validation> { validatedIngredients(listOf(Ingredient("x".repeat(81)))) }
        assertFailsWith<AppException.Validation> { validatedIngredients(listOf(Ingredient("Mehl", 1.0, "x".repeat(21)))) }
        assertFailsWith<AppException.Validation> { validatedIngredients(listOf(Ingredient("  "))) }
    }

    @Test
    fun `REQ-MEAL-062 more than 30 ingredients are rejected`() {
        assertFailsWith<AppException.Validation> { validatedIngredients((1..31).map { Ingredient("Zutat $it") }) }
    }
}
