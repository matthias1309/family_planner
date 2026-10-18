package de.familyplanner.android.feature.meals.domain.plan

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValidationTest {
    private fun message(result: ApiResult.Failure?) = (result?.error as? AppError.Validation)?.message

    @Test
    fun `REQ-MEAL-032 blank name is rejected`() {
        assertEquals("Bitte einen Namen eingeben.", message(validateDish("   ", "")))
    }

    @Test
    fun `REQ-MEAL-032 name longer than 60 characters is rejected`() {
        assertEquals("Der Name darf höchstens 60 Zeichen haben.", message(validateDish("a".repeat(61), "")))
    }

    @Test
    fun `REQ-MEAL-032 name of 60 characters after trimming is accepted`() {
        assertNull(validateDish("  " + "a".repeat(60) + "  ", ""))
    }

    @Test
    fun `REQ-MEAL-032 ingredient line longer than 80 characters is rejected`() {
        assertEquals("Eine Zutat darf höchstens 80 Zeichen haben.", message(validateDish("Suppe", "b".repeat(81))))
        assertNull(validateDish("Suppe", "b".repeat(80)))
    }

    @Test
    fun `REQ-MEAL-032 more than 30 ingredients are rejected`() {
        val text = (1..31).joinToString("\n") { "Zutat $it" }

        assertEquals("Höchstens 30 Zutaten je Gericht.", message(validateDish("Suppe", text)))
    }

    @Test
    fun `REQ-MEAL-032 30 ingredients with blank lines are accepted`() {
        val text = (1..30).joinToString("\n") { "Zutat $it" } + "\n".repeat(70)

        assertNull(validateDish("Suppe", text))
    }

    @Test
    fun `REQ-MEAL-032 more than 100 raw lines are rejected`() {
        val text = "Reis" + "\n".repeat(100)

        assertEquals("Höchstens 30 Zutaten je Gericht.", message(validateDish("Suppe", text)))
    }

    @Test
    fun `REQ-MEAL-032 empty ingredients are allowed`() {
        assertNull(validateDish("Suppe", ""))
        assertEquals(emptyList(), ingredientLines(""))
    }

    @Test
    fun `REQ-MEAL-032 parent dish without ingredients stays allowed`() {
        assertNull(validateDish("Suppe", "", ingredientsRequired = false))
    }

    @Test
    fun `REQ-MEAL-020 child dish without ingredients is rejected`() {
        assertEquals("Bitte mindestens eine Zutat angeben.", message(validateDish("Curry", "", ingredientsRequired = true)))
    }

    @Test
    fun `REQ-MEAL-020 child dish with blank lines only is rejected`() {
        assertEquals("Bitte mindestens eine Zutat angeben.", message(validateDish("Curry", "\n \n", ingredientsRequired = true)))
        assertNull(validateDish("Curry", "Reis", ingredientsRequired = true))
    }

    @Test
    fun `REQ-MEAL-023 blank reason is rejected`() {
        assertEquals("Bitte eine Begründung angeben.", message(validateReason("   ")))
    }

    @Test
    fun `REQ-MEAL-023 reason longer than 200 characters is rejected`() {
        assertEquals("Die Begründung darf höchstens 200 Zeichen haben.", message(validateReason("a".repeat(201))))
    }

    @Test
    fun `REQ-MEAL-030 reason of 200 characters after trimming is accepted`() {
        assertNull(validateReason("  " + "a".repeat(200) + "  "))
    }

    @Test
    fun `REQ-MEAL-032 ingredient lines are trimmed and blank lines dropped`() {
        assertEquals(listOf("Reis", "Erbsen"), ingredientLines(" Reis \n\n Erbsen"))
    }
}
