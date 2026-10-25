package de.familyplanner.gamification.ledger

import de.familyplanner.core.events.CookingConfirmed
import de.familyplanner.core.events.TaskAccepted
import de.familyplanner.gamification.fakes.GamificationFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointsEventHandlerTest {
    private val fixture = GamificationFixture()

    private fun accepted(
        memberId: String,
        card: String = "c1",
        points: Int = 2,
    ) = TaskAccepted(card, memberId, "Bad putzen", points, "p1", 5_000L)

    private fun cooked(
        memberId: String,
        dish: String = "d1",
    ) = CookingConfirmed(dish, memberId, "Spaghetti", "2026-10-14", "p1", 6_000L)

    @Test
    fun `REQ-GAME-010 accepted task credits its points`() =
        runTest {
            val child = fixture.child()

            fixture.handler.handle(fixture.stored(accepted(child.id.value)))

            val entry = fixture.ledger.entries.single()
            assertEquals(EntryKind.TASK, entry.kind)
            assertEquals(2, entry.amount)
            assertEquals("Bad putzen", entry.title)
            assertEquals(5_000L, entry.bookedAt.toEpochMilli())
        }

    @Test
    fun `REQ-GAME-010 same task event twice credits once`() =
        runTest {
            val child = fixture.child()
            val event = fixture.stored(accepted(child.id.value))

            fixture.handler.handle(event)
            fixture.handler.handle(event)

            assertEquals(1, fixture.ledger.entries.size)
        }

    @Test
    fun `REQ-GAME-011 confirmed cooking credits the cooking points`() =
        runTest {
            val child = fixture.child()

            fixture.handler.handle(fixture.stored(cooked(child.id.value)))

            val entry = fixture.ledger.entries.single()
            assertEquals(EntryKind.COOKING, entry.kind)
            assertEquals(3, entry.amount)
            assertEquals(6_000L, entry.bookedAt.toEpochMilli())
        }

    @Test
    fun `REQ-GAME-011 changed cooking points apply to later confirmations`() =
        runTest {
            val child = fixture.child()
            fixture.handler.handle(fixture.stored(cooked(child.id.value, "d1")))

            fixture.settings.update(5)
            fixture.handler.handle(fixture.stored(cooked(child.id.value, "d2"), 2))

            assertEquals(listOf(3, 5), fixture.ledger.entries.map { it.amount })
        }

    @Test
    fun `REQ-GAME-010 event for unknown or parent member is skipped`() =
        runTest {
            val parent = fixture.parent()

            fixture.handler.handle(fixture.stored(accepted("nobody")))
            fixture.handler.handle(fixture.stored(accepted(parent.id.value, "c2")))

            assertTrue(fixture.ledger.entries.isEmpty())
        }

    @Test
    fun `unknown event type is ignored`() =
        runTest {
            fixture.handler.handle(fixture.stored(null))

            assertTrue(fixture.ledger.entries.isEmpty())
        }
}
