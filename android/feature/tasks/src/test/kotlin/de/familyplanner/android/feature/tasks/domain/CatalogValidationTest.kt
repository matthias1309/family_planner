package de.familyplanner.android.feature.tasks.domain

import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogInput
import de.familyplanner.android.feature.tasks.domain.catalog.validateCatalogInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogValidationTest {
    private fun message(input: CatalogInput) = (validateCatalogInput(input) as AppError.Validation).message

    @Test
    fun `REQ-TASK-010 valid input passes`() {
        assertNull(validateCatalogInput(CatalogInput("Bad putzen", "🛁", 3)))
        assertNull(validateCatalogInput(CatalogInput(" x ", null, 100)))
    }

    @Test
    fun `REQ-TASK-010 title must be 1 to 40 characters`() {
        assertEquals("Titel muss zwischen 1 und 40 Zeichen lang sein", message(CatalogInput("  ", null, 1)))
        assertEquals("Titel muss zwischen 1 und 40 Zeichen lang sein", message(CatalogInput("x".repeat(41), null, 1)))
    }

    @Test
    fun `REQ-TASK-010 emoji and points are bounded`() {
        assertEquals("Symbol ist zu lang", message(CatalogInput("A", "x".repeat(17), 1)))
        assertEquals("Punkte müssen zwischen 1 und 100 liegen", message(CatalogInput("A", null, 0)))
        assertEquals("Punkte müssen zwischen 1 und 100 liegen", message(CatalogInput("A", null, 101)))
    }
}
