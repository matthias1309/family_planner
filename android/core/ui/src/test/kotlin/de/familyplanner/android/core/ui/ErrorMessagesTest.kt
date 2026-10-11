package de.familyplanner.android.core.ui

import de.familyplanner.android.core.data.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ErrorMessagesTest {
    private val allErrors =
        listOf(
            AppError.Network,
            AppError.Certificate,
            AppError.Unauthorized,
            AppError.InvalidPairingCode,
            AppError.Forbidden,
            AppError.NotFound,
            AppError.RateLimited,
            AppError.SetupDisabled,
            AppError.Validation("x"),
            AppError.Conflict("x"),
            AppError.Server,
        )

    @Test
    fun `every AppError variant has a message resource`() {
        allErrors.forEach { assertNotEquals(0, it.messageRes()) }
    }

    @Test
    fun `message resources are distinct per variant`() {
        assertEquals(allErrors.size, allErrors.map { it.messageRes() }.toSet().size)
    }
}
