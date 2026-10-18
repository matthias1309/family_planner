package de.familyplanner.meals.recipe

import de.familyplanner.core.error.AppException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RecipeRulesTest {
    @Test
    fun `REQ-MEAL-060 servings must be between 1 and 20`() {
        assertFailsWith<AppException.Validation> { validatedServings(0) }
        assertEquals(1, validatedServings(1))
        assertEquals(20, validatedServings(20))
        assertFailsWith<AppException.Validation> { validatedServings(21) }
    }

    @Test
    fun `REQ-MEAL-060 steps are trimmed and blank steps removed`() {
        assertEquals(listOf("a", "b"), validatedSteps(listOf(" a ", "", "b")))
        assertEquals(30, validatedSteps((1..30).map { "Schritt $it" } + List(5) { "" }).size)
        assertFailsWith<AppException.Validation> { validatedSteps((1..31).map { "Schritt $it" }) }
        assertFailsWith<AppException.Validation> { validatedSteps(listOf("x".repeat(1001))) }
        assertFailsWith<AppException.Validation> { validatedSteps(List(101) { "" }) }
    }

    @Test
    fun `REQ-MEAL-060 source url must be http or https`() {
        val url = "https://www.rewe.de/rezepte/x/"
        assertEquals(url, validatedSourceUrl(url))
        assertNull(validatedSourceUrl("  "))
        assertNull(validatedSourceUrl(null))
        listOf("ftp://x", "kein link", "https://", "https://x.de/" + "a".repeat(500)).forEach { invalid ->
            assertFailsWith<AppException.Validation>(invalid) { validatedSourceUrl(invalid) }
        }
    }

    private fun summary(
        id: String,
        name: String,
    ) = RecipeSummary(RecipeId(id), name, 4)

    @Test
    fun `REQ-MEAL-060 search matches name ignoring case and sorts`() {
        // Arrange
        val all = listOf(summary("1", "Quesadilla"), summary("2", "carbonara"), summary("3", "Äpfel-Crumble"))

        // Act + Assert
        assertEquals(listOf("2"), matchingRecipes(all, "CARBO").map { it.id.value })
        assertEquals(listOf("3"), matchingRecipes(all, "äpfel").map { it.id.value })
        assertEquals(listOf("2", "1", "3"), matchingRecipes(all, null).map { it.id.value })
        assertEquals(listOf("2", "1", "3"), matchingRecipes(all, "  ").map { it.id.value })
    }

    @Test
    fun `REQ-MEAL-060 too long query is rejected`() {
        assertFailsWith<AppException.Validation> { matchingRecipes(emptyList(), "x".repeat(61)) }
    }
}
