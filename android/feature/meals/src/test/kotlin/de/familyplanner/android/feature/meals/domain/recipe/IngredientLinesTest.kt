package de.familyplanner.android.feature.meals.domain.recipe

import kotlin.test.Test
import kotlin.test.assertEquals

class IngredientLinesTest {
    @Test
    fun `REQ-MEAL-062 quantity unit and name are split`() {
        assertEquals(Ingredient("Vollkornspaghetti", 500.0, "g"), parseIngredientLine("500.0 g Vollkornspaghetti"))
    }

    @Test
    fun `REQ-MEAL-062 plural unit in brackets is a unit`() {
        assertEquals(Ingredient("Lauch", 2.0, "Stange(n)"), parseIngredientLine("2.0 Stange(n) Lauch"))
        assertEquals(Ingredient("Knoblauch", 2.0, "Zehe(n)"), parseIngredientLine("2.0 Zehe(n) Knoblauch"))
        assertEquals(
            Ingredient("REWE Beste Wahl Kidney-Bohnen", 1.0, "Dose(n)"),
            parseIngredientLine("1.0 Dose(n) REWE Beste Wahl Kidney-Bohnen"),
        )
    }

    @Test
    fun `REQ-MEAL-062 quantity without unit keeps the rest as name`() {
        assertEquals(Ingredient("Eier", 2.0), parseIngredientLine("2.0 Eier"))
        assertEquals(Ingredient("Frühlingszwiebeln", 4.0), parseIngredientLine("4.0 Frühlingszwiebeln"))
        assertEquals(Ingredient("REWE Beste Wahl Tortilla-Wraps", 4.0), parseIngredientLine("4.0 REWE Beste Wahl Tortilla-Wraps"))
    }

    @Test
    fun `REQ-MEAL-062 known unit is matched ignoring case`() {
        assertEquals(Ingredient("Rapsöl", 2.0, "EL"), parseIngredientLine("2.0 EL Rapsöl"))
        assertEquals(Ingredient("Öl", 1.0, "el"), parseIngredientLine("1 el Öl"))
    }

    @Test
    fun `REQ-MEAL-062 line without leading number is a name`() {
        assertEquals(Ingredient("Salz"), parseIngredientLine("Salz"))
        assertEquals(Ingredient("Saft von 1 Limette"), parseIngredientLine("Saft von 1 Limette"))
        assertEquals(Ingredient("Kreuzkümmel (gemahlen)"), parseIngredientLine("Kreuzkümmel (gemahlen)"))
    }

    @Test
    fun `REQ-MEAL-062 optional prefix stays in the name`() {
        assertEquals(Ingredient("Optional: Bio-Bacon", 70.0, "g"), parseIngredientLine("70.0 g Optional: Bio-Bacon"))
    }

    @Test
    fun `REQ-MEAL-062 decimal comma is accepted`() {
        assertEquals(Ingredient("Lauch", 2.5, "Stange(n)"), parseIngredientLine("2,5 Stange(n) Lauch"))
    }

    @Test
    fun `REQ-MEAL-062 glued unit is accepted`() {
        assertEquals(Ingredient("Mehl", 500.0, "g"), parseIngredientLine("500g Mehl"))
    }

    @Test
    fun `REQ-MEAL-062 dot thousands separator`() {
        assertEquals(Ingredient("Mehl", 1000.0, "g"), parseIngredientLine("1.000 g Mehl"))
        assertEquals(Ingredient("Milch", 0.125, "l"), parseIngredientLine("0.125 l Milch"))
    }

    @Test
    fun `REQ-MEAL-062 out of range quantity keeps the whole line`() {
        assertEquals(Ingredient("0 g Salz"), parseIngredientLine("0 g Salz"))
        assertEquals(Ingredient("20000 g Mehl"), parseIngredientLine("20000 g Mehl"))
    }

    @Test
    fun `REQ-MEAL-062 unreadable quantity keeps the whole line`() {
        assertEquals(Ingredient("2.5.1 g Mehl"), parseIngredientLine("2.5.1 g Mehl"))
    }

    @Test
    fun `REQ-MEAL-062 two word unit only fallback`() {
        assertEquals(Ingredient("große Zwiebeln", 2.0), parseIngredientLine("2 große Zwiebeln"))
    }

    @Test
    fun `REQ-MEAL-062 unit without name is a name`() {
        assertEquals(Ingredient("Würfel", 2.0), parseIngredientLine("2 Würfel"))
    }

    @Test
    fun `REQ-MEAL-062 display text matches backend format`() {
        assertEquals("500 g Vollkornspaghetti", Ingredient("Vollkornspaghetti", 500.0, "g").displayText())
        assertEquals("2,5 Stange(n) Lauch", Ingredient("Lauch", 2.5, "Stange(n)").displayText())
        assertEquals("Salz", Ingredient("Salz").displayText())
        assertEquals("0,333 Stück Brot", Ingredient("Brot", 0.333, "Stück").displayText())
    }

    @Test
    fun `REQ-MEAL-062 display text round trips through the parser`() {
        val samples =
            listOf(
                Ingredient("Vollkornspaghetti", 625.0, "g"),
                Ingredient("Lauch", 2.5, "Stange(n)"),
                Ingredient("Eier", 2.0),
                Ingredient("Salz"),
                Ingredient("Mehl", 1000.0, "g"),
                Ingredient("Milch", 0.125, "l"),
            )
        samples.forEach { assertEquals(it, parseIngredientLine(it.displayText())) }
    }

    @Test
    fun `REQ-MEAL-062 text lines are trimmed and blank lines dropped`() {
        assertEquals(
            listOf(Ingredient("Spaghetti", 500.0, "g"), Ingredient("Salz")),
            ingredientsFromText("  500 g Spaghetti \n\n  Salz\n"),
        )
    }

    @Test
    fun `REQ-MEAL-062 ingredients become text lines`() {
        assertEquals(
            "500 g Spaghetti\nSalz",
            ingredientsToText(listOf(Ingredient("Spaghetti", 500.0, "g"), Ingredient("Salz"))),
        )
    }
}
