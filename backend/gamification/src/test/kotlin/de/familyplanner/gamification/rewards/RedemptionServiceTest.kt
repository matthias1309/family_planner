package de.familyplanner.gamification.rewards

import de.familyplanner.core.error.AppException
import de.familyplanner.gamification.account.Actor
import de.familyplanner.gamification.fakes.GamificationFixture
import de.familyplanner.gamification.ledger.EntryKind
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedemptionServiceTest {
    private val fixture = GamificationFixture()

    private suspend fun reward(
        parent: Actor,
        price: Int = 200,
    ) = fixture.rewards.create(parent, RewardInput("Freizeitpark", null, price))

    private suspend fun balanceOf(child: Actor) =
        fixture.accounts
            .account(child, child.id, 10)
            .totals.balance

    @Test
    fun `REQ-GAME-042 redeem debits price and waits for parents`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent)
            fixture.accounts.book(parent, child.id, 250, "Start")

            val redemption = fixture.redemptions.redeem(child, created.id)

            assertEquals(RedemptionStatus.PENDING, redemption.status)
            assertEquals(200, redemption.price)
            assertEquals("Freizeitpark", redemption.title)
            assertEquals(50, balanceOf(child))
            val debit =
                fixture.accounts
                    .account(child, child.id, 10)
                    .entries
                    .first()
            assertEquals(EntryKind.REWARD, debit.kind)
            assertEquals(-200, debit.amount)
            assertEquals("Belohnung: Freizeitpark", debit.title)
        }

    @Test
    fun `REQ-GAME-042 balance below price is rejected (409) and nothing is booked`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent)
            fixture.accounts.book(parent, child.id, 199, "Start")

            assertFailsWith<AppException.Conflict> { fixture.redemptions.redeem(child, created.id) }

            assertEquals(199, balanceOf(child))
            assertTrue(fixture.redemptions.list(parent, null, 10).isEmpty())
        }

    @Test
    fun `REQ-GAME-042 balance exactly price is enough`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent)
            fixture.accounts.book(parent, child.id, 200, "Start")

            fixture.redemptions.redeem(child, created.id)

            assertEquals(0, balanceOf(child))
        }

    @Test
    fun `REQ-GAME-042 parent cannot redeem (403) and unknown reward is 404`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent)

            assertFailsWith<AppException.Forbidden> { fixture.redemptions.redeem(parent, created.id) }
            assertFailsWith<AppException.NotFound> { fixture.redemptions.redeem(child, RewardId("nope")) }
        }

    @Test
    fun `REQ-GAME-042 archived reward cannot be redeemed but running redemptions stay (REQ-GAME-040)`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent, price = 50)
            fixture.accounts.book(parent, child.id, 200, "Start")
            val running = fixture.redemptions.redeem(child, created.id)

            fixture.rewards.archive(parent, created.id)

            assertFailsWith<AppException.Conflict> { fixture.redemptions.redeem(child, created.id) }
            assertEquals(RedemptionStatus.FULFILLED, fixture.redemptions.fulfill(parent, running.id).status)
        }

    @Test
    fun `REQ-GAME-042 redeeming the goal reward clears the goal`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent, price = 100)
            fixture.accounts.book(parent, child.id, 100, "Start")
            fixture.rewards.setGoal(child, child.id, created.id)

            fixture.redemptions.redeem(child, created.id)

            assertNull(fixture.rewards.goal(child, child.id).reward)
        }

    @Test
    fun `REQ-GAME-042 redemption does not change collected`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent, price = 100)
            fixture.accounts.book(parent, child.id, 100, "Start")

            fixture.redemptions.redeem(child, created.id)

            val totals = fixture.accounts.account(child, child.id, 10).totals
            assertEquals(100, totals.collected)
            assertEquals(0, totals.balance)
        }

    @Test
    fun `REQ-GAME-042 price change after redeem keeps the snapshot`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent)
            fixture.accounts.book(parent, child.id, 200, "Start")
            val redemption = fixture.redemptions.redeem(child, created.id)

            fixture.rewards.update(parent, created.id, RewardInput("Teurer", null, 300))

            assertEquals(
                200,
                fixture.redemptions
                    .list(parent, null, 10)
                    .single { it.id == redemption.id }
                    .price,
            )
        }

    @Test
    fun `REQ-GAME-042 child lists only own redemptions, parent lists all, status filter`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val sibling = fixture.child()
            val created = reward(parent, price = 10)
            fixture.accounts.book(parent, child.id, 100, "Start")
            fixture.accounts.book(parent, sibling.id, 100, "Start")
            val first = fixture.redemptions.redeem(child, created.id)
            fixture.clock.advance(Duration.ofMinutes(1))
            val second = fixture.redemptions.redeem(sibling, created.id)
            fixture.clock.advance(Duration.ofMinutes(1))
            val third = fixture.redemptions.redeem(child, created.id)
            fixture.redemptions.fulfill(parent, first.id)

            assertEquals(listOf(third.id, first.id), fixture.redemptions.list(child, null, 10).map { it.id })
            assertEquals(listOf(third.id, second.id, first.id), fixture.redemptions.list(parent, null, 10).map { it.id })
            assertEquals(listOf(third.id, second.id), fixture.redemptions.list(parent, RedemptionStatus.PENDING, 10).map { it.id })
            assertEquals(1, fixture.redemptions.list(parent, null, 1).size)
            assertFailsWith<AppException.Validation> { fixture.redemptions.list(parent, null, 0) }
        }

    @Test
    fun `REQ-GAME-043 fulfill completes pending redemption`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent, price = 10)
            fixture.accounts.book(parent, child.id, 10, "Start")
            val redemption = fixture.redemptions.redeem(child, created.id)

            val done = fixture.redemptions.fulfill(parent, redemption.id)

            assertEquals(RedemptionStatus.FULFILLED, done.status)
            assertEquals(fixture.clock.instant(), done.decidedAt)
            assertEquals(0, balanceOf(child))
        }

    @Test
    fun `REQ-GAME-043 reject refunds price and keeps reason`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent)
            fixture.accounts.book(parent, child.id, 200, "Start")
            val redemption = fixture.redemptions.redeem(child, created.id)

            val rejected = fixture.redemptions.reject(parent, redemption.id, "Erst im Sommer")

            assertEquals(RedemptionStatus.REJECTED, rejected.status)
            assertEquals("Erst im Sommer", rejected.rejectionReason)
            assertEquals(200, balanceOf(child))
            val refund =
                fixture.accounts
                    .account(child, child.id, 10)
                    .entries
                    .first()
            assertEquals(EntryKind.REWARD_REFUND, refund.kind)
            assertEquals(200, refund.amount)
            assertEquals("Zurückgebucht: Freizeitpark", refund.title)
        }

    @Test
    fun `REQ-GAME-043 reject without reason is rejected (400)`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent, price = 10)
            fixture.accounts.book(parent, child.id, 10, "Start")
            val redemption = fixture.redemptions.redeem(child, created.id)

            assertFailsWith<AppException.Validation> { fixture.redemptions.reject(parent, redemption.id, "  ") }
            assertFailsWith<AppException.Validation> {
                fixture.redemptions.reject(parent, redemption.id, "x".repeat(REJECTION_REASON_MAX + 1))
            }
            assertEquals(0, balanceOf(child))
        }

    @Test
    fun `REQ-GAME-043 deciding twice is a conflict and refunds only once`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent)
            fixture.accounts.book(parent, child.id, 200, "Start")
            val redemption = fixture.redemptions.redeem(child, created.id)
            fixture.redemptions.reject(parent, redemption.id, "Nein")

            assertFailsWith<AppException.Conflict> { fixture.redemptions.reject(parent, redemption.id, "Nein") }
            assertFailsWith<AppException.Conflict> { fixture.redemptions.fulfill(parent, redemption.id) }

            assertEquals(200, balanceOf(child))
        }

    @Test
    fun `REQ-GAME-043 child cannot decide (403)`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent, price = 10)
            fixture.accounts.book(parent, child.id, 10, "Start")
            val redemption = fixture.redemptions.redeem(child, created.id)

            assertFailsWith<AppException.Forbidden> { fixture.redemptions.fulfill(child, redemption.id) }
            assertFailsWith<AppException.Forbidden> { fixture.redemptions.reject(child, redemption.id, "Nein") }
        }

    @Test
    fun `REQ-GAME-043 unknown redemption 404`() =
        runTest {
            val parent = fixture.parent()

            assertFailsWith<AppException.NotFound> { fixture.redemptions.fulfill(parent, RedemptionId("nope")) }
            assertFailsWith<AppException.NotFound> { fixture.redemptions.reject(parent, RedemptionId("nope"), "Nein") }
        }
}
