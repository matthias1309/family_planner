package de.familyplanner.android.feature.meals.domain.recipe

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreBrandsTest {
    @Test
    fun `REQ-MEAL-061 store brand prefixes are removed`() {
        assertEquals("Rapsöl", stripStoreBrand("REWE Bio Rapsöl"))
        assertEquals("Gouda gerieben", stripStoreBrand("ja! Gouda gerieben"))
        assertEquals("Pesto", stripStoreBrand("REWE Feine Welt Pesto"))
        assertEquals("Rapsöl", stripStoreBrand("rewe bio Rapsöl"))
    }

    @Test
    fun `REQ-MEAL-061 longest brand wins`() {
        assertEquals("Kidney-Bohnen", stripStoreBrand("REWE Beste Wahl Kidney-Bohnen"))
    }

    @Test
    fun `REQ-MEAL-061 nothing left keeps the original name`() {
        assertEquals("REWE", stripStoreBrand("REWE"))
        assertEquals("REWE Bio", stripStoreBrand("REWE Bio"))
    }

    @Test
    fun `REQ-MEAL-061 only whole words are brands`() {
        assertEquals("Optional: Bio-Bacon", stripStoreBrand("Optional: Bio-Bacon"))
        assertEquals("REWEnudeln", stripStoreBrand("REWEnudeln"))
    }
}
