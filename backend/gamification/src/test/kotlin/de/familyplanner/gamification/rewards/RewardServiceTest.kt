package de.familyplanner.gamification.rewards

import de.familyplanner.core.error.AppException
import de.familyplanner.gamification.account.Actor
import de.familyplanner.gamification.fakes.GamificationFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewardServiceTest {
    private val fixture = GamificationFixture()

    private suspend fun reward(
        parent: Actor,
        price: Int = 200,
    ) = fixture.rewards.create(parent, RewardInput("Freizeitpark", "🎢", price))

    @Test
    fun `REQ-GAME-040 parent creates reward and every child sees it`() =
        runTest {
            val parent = fixture.parent()
            val created = reward(parent)

            val seen = fixture.rewards.list(includeArchived = false)

            assertEquals(listOf(created), seen)
            assertEquals(200, created.price)
            assertFalse(created.archived)
        }

    @Test
    fun `REQ-GAME-040 child cannot create, update or archive (403)`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent)

            assertFailsWith<AppException.Forbidden> { fixture.rewards.create(child, RewardInput("x", null, 5)) }
            assertFailsWith<AppException.Forbidden> { fixture.rewards.update(child, created.id, RewardInput("x", null, 5)) }
            assertFailsWith<AppException.Forbidden> { fixture.rewards.archive(child, created.id) }
        }

    @Test
    fun `REQ-GAME-040 archived reward is hidden by default, listed with includeArchived and cannot be updated (409)`() =
        runTest {
            val parent = fixture.parent()
            val created = reward(parent)

            fixture.rewards.archive(parent, created.id)
            fixture.rewards.archive(parent, created.id)

            assertTrue(fixture.rewards.list(includeArchived = false).isEmpty())
            assertEquals(listOf(true), fixture.rewards.list(includeArchived = true).map { it.archived })
            assertFailsWith<AppException.Conflict> { fixture.rewards.update(parent, created.id, RewardInput("Neu", null, 10)) }
        }

    @Test
    fun `REQ-GAME-040 update changes fields and invalid input is rejected`() =
        runTest {
            val parent = fixture.parent()
            val created = reward(parent)

            val updated = fixture.rewards.update(parent, created.id, RewardInput(" Kino ", null, 80))

            assertEquals("Kino", updated.title)
            assertEquals(80, updated.price)
            assertFailsWith<AppException.Validation> { fixture.rewards.create(parent, RewardInput("", null, 5)) }
        }

    @Test
    fun `REQ-GAME-040 unknown reward 404`() =
        runTest {
            val parent = fixture.parent()
            val unknown = RewardId("nope")

            assertFailsWith<AppException.NotFound> { fixture.rewards.update(parent, unknown, RewardInput("x", null, 5)) }
            assertFailsWith<AppException.NotFound> { fixture.rewards.archive(parent, unknown) }
        }

    @Test
    fun `REQ-GAME-041 child sets goal and sees 120 of 200`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent)
            fixture.accounts.book(parent, child.id, 120, "Start")

            val goal = fixture.rewards.setGoal(child, child.id, created.id)

            assertEquals(created, goal.reward)
            assertEquals(120, goal.balance)
            assertEquals(120, goal.progress)
            assertFalse(goal.redeemable)
        }

    @Test
    fun `REQ-GAME-041 parent reads goal but cannot set it`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent)
            fixture.rewards.setGoal(child, child.id, created.id)

            assertEquals(created, fixture.rewards.goal(parent, child.id).reward)
            assertFailsWith<AppException.Forbidden> { fixture.rewards.setGoal(parent, child.id, created.id) }
            assertFailsWith<AppException.Forbidden> { fixture.rewards.clearGoal(parent, child.id) }
        }

    @Test
    fun `REQ-GAME-041 sibling cannot read goal`() =
        runTest {
            val child = fixture.child()
            val sibling = fixture.child()

            assertFailsWith<AppException.Forbidden> { fixture.rewards.goal(sibling, child.id) }
            assertFailsWith<AppException.Forbidden> { fixture.rewards.setGoal(sibling, child.id, RewardId("x")) }
        }

    @Test
    fun `REQ-GAME-041 archived reward cannot become a goal (409)`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent)
            fixture.rewards.archive(parent, created.id)

            assertFailsWith<AppException.Conflict> { fixture.rewards.setGoal(child, child.id, created.id) }
            assertFailsWith<AppException.NotFound> { fixture.rewards.setGoal(child, child.id, RewardId("nope")) }
        }

    @Test
    fun `REQ-GAME-041 goal on a later archived reward stays but is not redeemable`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            val created = reward(parent, price = 50)
            fixture.accounts.book(parent, child.id, 80, "Start")
            fixture.rewards.setGoal(child, child.id, created.id)

            fixture.rewards.archive(parent, created.id)
            val goal = fixture.rewards.goal(child, child.id)

            assertEquals(true, goal.reward?.archived)
            assertFalse(goal.redeemable)
        }

    @Test
    fun `REQ-GAME-041 no goal returns empty goal with balance`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            fixture.accounts.book(parent, child.id, 30, "Start")

            val goal = fixture.rewards.goal(child, child.id)

            assertNull(goal.reward)
            assertNull(goal.progress)
            assertEquals(30, goal.balance)
        }

    @Test
    fun `REQ-GAME-041 clearing twice is fine`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()
            fixture.rewards.setGoal(child, child.id, reward(parent).id)

            fixture.rewards.clearGoal(child, child.id)
            fixture.rewards.clearGoal(child, child.id)

            assertNull(fixture.rewards.goal(child, child.id).reward)
        }

    @Test
    fun `REQ-GAME-041 goal of a parent member is rejected`() =
        runTest {
            val parent = fixture.parent()

            assertFailsWith<AppException.Validation> { fixture.rewards.goal(parent, parent.id) }
        }
}
