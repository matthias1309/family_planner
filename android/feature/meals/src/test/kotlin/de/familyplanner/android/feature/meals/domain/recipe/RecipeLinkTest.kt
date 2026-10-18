package de.familyplanner.android.feature.meals.domain.recipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecipeLinkTest {
    private val link = "https://www.rewe.de/rezepte/x/"

    @Test
    fun `REQ-MEAL-061 plain link is returned`() {
        assertEquals(link, extractRecipeUrl(link))
    }

    @Test
    fun `REQ-MEAL-061 link is found in shared text`() {
        assertEquals(link, extractRecipeUrl("Spaghetti mit Lauch-Carbonara $link"))
    }

    @Test
    fun `REQ-MEAL-061 trailing punctuation is dropped`() {
        assertEquals(link, extractRecipeUrl("($link)"))
        assertEquals(link, extractRecipeUrl("Schau mal: $link."))
    }

    @Test
    fun `REQ-MEAL-061 first of two links wins`() {
        assertEquals(link, extractRecipeUrl("$link und https://example.org/"))
    }

    @Test
    fun `REQ-MEAL-061 no http link gives null`() {
        assertNull(extractRecipeUrl("ftp://example.org/x"))
        assertNull(extractRecipeUrl("rewe.de/rezepte"))
        assertNull(extractRecipeUrl(""))
    }
}
