package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ValidationTest {
    private fun check(
        name: String = "Carbonara",
        servings: Int = 4,
        ingredients: String = "500 g Spaghetti",
        steps: String = "",
        url: String = "",
    ) = validateRecipe(name, servings, ingredients, steps, url)

    private fun message(failure: ApiResult.Failure?): String? = (failure?.error as? AppError.Validation)?.message

    @Test
    fun `REQ-MEAL-060 recipe without name is rejected`() {
        assertEquals("Bitte einen Namen eingeben.", message(check(name = " ")))
    }

    @Test
    fun `REQ-MEAL-060 recipe without ingredients is rejected`() {
        assertEquals("Bitte mindestens eine Zutat angeben.", message(check(ingredients = " \n ")))
    }

    @Test
    fun `REQ-MEAL-060 servings below one are rejected`() {
        assertNotNull(check(servings = 0))
    }

    @Test
    fun `REQ-MEAL-060 servings above twenty are rejected`() {
        assertNotNull(check(servings = 21))
    }

    @Test
    fun `REQ-MEAL-060 more than thirty steps are rejected`() {
        assertEquals("Höchstens 30 Schritte.", message(check(steps = (1..31).joinToString("\n") { "Schritt $it" })))
    }

    @Test
    fun `REQ-MEAL-060 thirty steps are allowed`() {
        assertNull(check(steps = (1..30).joinToString("\n") { "Schritt $it" }))
    }

    @Test
    fun `REQ-MEAL-060 step longer than 1000 characters is rejected`() {
        assertEquals("Ein Schritt darf höchstens 1000 Zeichen haben.", message(check(steps = "x".repeat(1001))))
    }

    @Test
    fun `REQ-MEAL-060 invalid source url is rejected`() {
        listOf("ftp://x", "rewe.de/rezepte", "https://", "https://a b.de").forEach {
            assertEquals("Bitte einen gültigen Link (http/https) angeben.", message(check(url = it)))
        }
    }

    @Test
    fun `REQ-MEAL-060 too long source url is rejected`() {
        assertNotNull(check(url = "https://rewe.de/" + "a".repeat(RecipeLimits.SOURCE_URL_MAX)))
    }

    @Test
    fun `REQ-MEAL-060 empty source url is allowed`() {
        assertNull(check(url = "  "))
    }

    @Test
    fun `REQ-MEAL-060 valid recipe passes`() {
        assertNull(check(steps = "Nudeln kochen.\n\nServieren.", url = "https://www.rewe.de/rezepte/x/"))
    }

    @Test
    fun `REQ-MEAL-060 step lines are trimmed and blank lines dropped`() {
        assertEquals(listOf("a", "b"), stepLines(" a \n\n b\n"))
    }
}
