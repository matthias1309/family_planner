package de.familyplanner.android.feature.meals.domain.attendance

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ValidationTest {
    private val from = LocalDate.of(2026, 12, 20)

    private fun messageOf(failure: ApiResult.Failure?): String? = (failure?.error as? AppError.Validation)?.message

    @Test
    fun `REQ-MEAL-010 alternating pattern needs at least one day`() {
        val empty = AttendancePattern.Alternating(emptySet(), emptySet(), from)

        assertEquals("Bitte mindestens einen Anwesenheitstag wählen.", messageOf(validatePattern(empty)))
        assertNull(validatePattern(empty.copy(weekB = setOf(DayOfWeek.MONDAY))))
        assertNull(validatePattern(AttendancePattern.Always))
    }

    @Test
    fun `REQ-MEAL-011 exception end before start is rejected`() {
        val failure = validateException(from, from.minusDays(1), null)

        assertIs<ApiResult.Failure>(failure)
        assertEquals("Das Ende darf nicht vor dem Beginn liegen.", messageOf(failure))
    }

    @Test
    fun `REQ-MEAL-011 exception longer than 366 days is rejected`() {
        assertNull(validateException(from, from.plusDays(365), null))
        assertEquals("Eine Ausnahme darf höchstens 366 Tage dauern.", messageOf(validateException(from, from.plusDays(366), null)))
    }

    @Test
    fun `REQ-MEAL-011 exception outside supported years is rejected`() {
        val message = "Bitte ein Datum zwischen 2000 und 2100 wählen."

        assertEquals(message, messageOf(validateException(LocalDate.of(1999, 12, 31), from, null)))
        assertEquals(message, messageOf(validateException(from, LocalDate.of(2101, 1, 1), null)))
    }

    @Test
    fun `REQ-MEAL-011 note longer than 60 characters is rejected`() {
        assertNull(validateException(from, from, "x".repeat(60)))
        assertEquals("Die Notiz darf höchstens 60 Zeichen haben.", messageOf(validateException(from, from, "x".repeat(61))))
    }

    @Test
    fun `REQ-MEAL-011 blank note becomes null`() {
        assertNull(normalizeNote("   "))
        assertNull(normalizeNote(null))
        assertEquals("Ferien", normalizeNote("  Ferien "))
    }
}
