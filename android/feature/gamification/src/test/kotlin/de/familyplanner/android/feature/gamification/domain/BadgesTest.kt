package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.feature.gamification.fakes.badge
import de.familyplanner.android.feature.gamification.fakes.overview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadgesTest {
    private val now = overview(badge("first-task", earned = true), badge("tasks-10", earned = true), badge("tasks-50"))

    @Test
    fun `REQ-GAME-020 newly earned is empty without a previous overview`() {
        assertTrue(now.newlyEarned(null).isEmpty())
    }

    @Test
    fun `REQ-GAME-020 newly earned lists only badges earned since the previous overview`() {
        val previous = overview(badge("first-task", earned = true), badge("tasks-10"), badge("tasks-50"))

        assertEquals(listOf("tasks-10"), now.newlyEarned(previous).map { it.id })
    }

    @Test
    fun `REQ-GAME-021 a secret badge without title is masked`() {
        assertTrue(badge("secret-1", secret = true, title = null, progress = null).masked)
    }
}
