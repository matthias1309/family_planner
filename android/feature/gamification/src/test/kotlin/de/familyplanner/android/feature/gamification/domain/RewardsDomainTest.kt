package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.feature.gamification.fakes.reward
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewardsDomainTest {
    @Test
    fun `REQ-GAME-040 reward draft needs title and price in range`() {
        assertTrue(RewardDraft(title = "Kino", priceText = "50").canSubmit)
        assertFalse(RewardDraft(title = " ", priceText = "50").canSubmit)
        assertFalse(RewardDraft(title = "Kino", priceText = "").canSubmit)
        assertFalse(RewardDraft(title = "Kino", priceText = "0").canSubmit)
        assertFalse(RewardDraft(title = "Kino", priceText = "10001").canSubmit)
        assertTrue(RewardDraft(title = "Kino", priceText = "10000").canSubmit)
        assertFalse(RewardDraft(title = "x".repeat(RewardLimits.TITLE_MAX + 1), priceText = "5").canSubmit)
        assertFalse(RewardDraft(title = "Kino", emoji = "x".repeat(RewardLimits.EMOJI_MAX + 1), priceText = "5").canSubmit)
    }

    @Test
    fun `REQ-GAME-040 reward draft from reward keeps values`() {
        val draft = RewardDraft.of(reward("r-1", price = 80, title = "Pizza", emoji = null))

        assertEquals(RewardDraft("r-1", "Pizza", "", "80"), draft)
    }

    @Test
    fun `REQ-GAME-043 rejection needs a reason`() {
        assertNotNull(validateRejection("  "))
        assertNotNull(validateRejection("x".repeat(RewardLimits.REASON_MAX + 1)))
        assertNull(validateRejection("Ferien"))
    }
}
