package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.feature.gamification.fakes.leaderboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ValidationTest {
    @Test
    fun `REQ-GAME-013 manual entry needs a non-zero amount up to 1000`() {
        assertNotNull(validateManualEntry(0, "x"))
        assertNotNull(validateManualEntry(1001, "x"))
        assertNotNull(validateManualEntry(-1001, "x"))
        assertNull(validateManualEntry(-2, "x"))
        assertNull(validateManualEntry(1000, "x"))
    }

    @Test
    fun `REQ-GAME-013 manual entry needs a reason of 1 to 200 characters`() {
        assertNotNull(validateManualEntry(1, "  "))
        assertNotNull(validateManualEntry(1, "a".repeat(201)))
        assertNull(validateManualEntry(1, " ok "))
    }

    @Test
    fun `REQ-GAME-011 cooking points must be between 1 and 20`() {
        assertNotNull(validateCookingPoints(0))
        assertNotNull(validateCookingPoints(21))
        assertNull(validateCookingPoints(3))
    }

    @Test
    fun `REQ-GAME-014 rankOf finds the child or null`() {
        assertEquals(2, leaderboard.rankOf("c-1")?.rank)
        assertNull(leaderboard.rankOf("x"))
    }
}
