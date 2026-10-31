package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.feature.gamification.domain.notice.RedemptionSnapshot
import de.familyplanner.android.feature.gamification.domain.notice.RewardNotice
import de.familyplanner.android.feature.gamification.domain.notice.rewardNotices
import de.familyplanner.android.feature.gamification.fakes.noon
import de.familyplanner.android.feature.gamification.fakes.redemption
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

class RewardNoticesTest {
    private val names = mapOf("c-2" to "Ben")
    private val before = RedemptionSnapshot(mapOf("d-old" to RedemptionStatus.PENDING), noon)

    @Test
    fun `REQ-GAME-042 no baseline gives no notices`() {
        assertEquals(emptyList(), rewardNotices(true, null, listOf(redemption("d-1")), names))
        assertEquals(emptyList(), rewardNotices(false, null, listOf(redemption("d-1", RedemptionStatus.FULFILLED)), names))
    }

    @Test
    fun `REQ-GAME-042 parent is told about new pending redemptions only`() {
        val current = listOf(redemption("d-old"), redemption("d-1", memberId = "c-2"))

        assertEquals(listOf(RewardNotice.Requested("d-1", "Ben", "Pizzaabend", "🍕")), rewardNotices(true, before, current, names))
    }

    @Test
    fun `REQ-GAME-042 parent notice without name has null child name`() {
        val notices = rewardNotices(true, before, listOf(redemption("d-1", memberId = "c-9")), names)

        assertEquals(null, (notices.single() as RewardNotice.Requested).childName)
    }

    @Test
    fun `REQ-GAME-043 child is told when a pending redemption is fulfilled or rejected`() {
        val current =
            listOf(
                redemption("d-old", RedemptionStatus.FULFILLED),
                redemption("d-2", RedemptionStatus.REJECTED, reason = "Ferien"),
            )
        val previous = RedemptionSnapshot(mapOf("d-old" to RedemptionStatus.PENDING, "d-2" to RedemptionStatus.PENDING), noon)

        assertEquals(
            listOf(
                RewardNotice.Fulfilled("d-old", "Pizzaabend", "🍕"),
                RewardNotice.Rejected("d-2", "Pizzaabend", "🍕", "Ferien"),
            ),
            rewardNotices(false, previous, current, emptyMap()),
        )
    }

    @Test
    fun `REQ-GAME-043 child own new pending redemption gives no notice`() {
        assertEquals(emptyList(), rewardNotices(false, before, listOf(redemption("d-1")), emptyMap()))
    }

    @Test
    fun `REQ-GAME-043 child is told about unknown redemption decided after the last sync`() {
        val late = redemption("d-1", RedemptionStatus.FULFILLED, decidedAt = noon.plus(Duration.ofMinutes(5)))
        val early = redemption("d-2", RedemptionStatus.FULFILLED, decidedAt = noon.minus(Duration.ofMinutes(5)))

        val notices = rewardNotices(false, before, listOf(late, early), emptyMap())

        assertEquals(listOf<RewardNotice>(RewardNotice.Fulfilled("d-1", "Pizzaabend", "🍕")), notices)
    }

    @Test
    fun `REQ-GAME-043 already decided redemption is not announced twice`() {
        val previous = RedemptionSnapshot(mapOf("d-1" to RedemptionStatus.FULFILLED), noon)

        assertEquals(emptyList(), rewardNotices(false, previous, listOf(redemption("d-1", RedemptionStatus.FULFILLED)), emptyMap()))
    }
}
