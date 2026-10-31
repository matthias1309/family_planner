package de.familyplanner.gamification.rewards

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewardTest {
    private fun reward(
        price: Int = 200,
        archived: Boolean = false,
    ) = Reward(RewardId("r"), "Freizeitpark", null, price, archived)

    private fun goal(
        balance: Int,
        reward: Reward? = reward(),
    ) = SavingsGoal(MemberId("m"), reward, balance)

    @Test
    fun `REQ-GAME-040 title is trimmed and blank emoji becomes null`() {
        val result = validated(RewardInput("  Eis  ", "  ", 50))

        assertEquals(RewardInput("Eis", null, 50), result)
    }

    @Test
    fun `REQ-GAME-040 price 0, 10001 or title over 40 is rejected`() {
        assertFailsWith<AppException.Validation> { validated(RewardInput("Eis", null, 0)) }
        assertFailsWith<AppException.Validation> { validated(RewardInput("Eis", null, REWARD_PRICE_MAX + 1)) }
        assertFailsWith<AppException.Validation> { validated(RewardInput("x".repeat(REWARD_TITLE_MAX + 1), null, 5)) }
        assertFailsWith<AppException.Validation> { validated(RewardInput("Eis", "e".repeat(REWARD_EMOJI_MAX + 1), 5)) }
        assertEquals(REWARD_PRICE_MAX, validated(RewardInput("Eis", null, REWARD_PRICE_MAX)).price)
    }

    @Test
    fun `REQ-GAME-041 progress is balance clamped to 0 and price`() {
        assertEquals(120, goal(120).progress)
        assertEquals(0, goal(-20).progress)
        assertEquals(200, goal(250).progress)
        assertNull(goal(100, reward = null).progress)
    }

    @Test
    fun `REQ-GAME-041 archived goal is not redeemable`() {
        assertTrue(goal(200).redeemable)
        assertFalse(goal(199).redeemable)
        assertFalse(goal(500, reward(archived = true)).redeemable)
        assertFalse(goal(500, reward = null).redeemable)
    }
}
