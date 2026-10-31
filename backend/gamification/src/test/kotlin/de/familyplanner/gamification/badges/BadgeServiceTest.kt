package de.familyplanner.gamification.badges

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.gamification.fakes.GamificationFixture
import de.familyplanner.gamification.ledger.EntryKind
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BadgeServiceTest {
    private val fixture = GamificationFixture()

    private suspend fun book(
        memberId: MemberId,
        kind: EntryKind,
        amount: Int,
        at: String = "2026-10-24T10:00:00Z",
    ) = fixture.ledger.append(ledgerEntry(kind, amount, at, memberId))

    private fun awarded(memberId: MemberId) =
        fixture.badgeRepository.awards
            .filterKeys { it.first == memberId }
            .mapKeys { it.key.second }

    @Test
    fun `REQ-GAME-020 tenth cooking awards cooking-10`() =
        runTest {
            val child = fixture.child()
            repeat(9) { book(child.id, EntryKind.COOKING, 3) }
            fixture.badges.evaluate(child.id)
            assertFalse("cooking-10" in awarded(child.id))

            book(child.id, EntryKind.COOKING, 3)
            fixture.clock.advance(Duration.ofDays(1))
            fixture.badges.evaluate(child.id)

            assertEquals(Instant.parse("2026-10-25T10:00:00Z"), awarded(child.id)["cooking-10"])
        }

    @Test
    fun `REQ-GAME-020 badge is awarded only once and never revoked`() =
        runTest {
            val child = fixture.child()
            book(child.id, EntryKind.MANUAL, 100)
            fixture.badges.evaluate(child.id)
            val first = awarded(child.id)["points-100"]
            assertNotNull(first)

            fixture.clock.advance(Duration.ofDays(6))
            book(child.id, EntryKind.MANUAL, -50)
            fixture.badges.evaluate(child.id)

            assertEquals(first, awarded(child.id)["points-100"])
        }

    @Test
    fun `REQ-GAME-020 evaluateAll awards for existing data of every child`() =
        runTest {
            val first = fixture.child()
            val second = fixture.child()
            val parent = fixture.parent()
            book(first.id, EntryKind.TASK, 1)
            book(second.id, EntryKind.COOKING, 3)
            book(parent.id, EntryKind.TASK, 1)

            fixture.badges.evaluateAll()

            assertTrue("first-task" in awarded(first.id))
            assertTrue("first-cooking" in awarded(second.id))
            assertTrue(awarded(parent.id).isEmpty())
        }

    @Test
    fun `REQ-GAME-021 own overview lists open badges with progress`() =
        runTest {
            val child = fixture.child()
            repeat(7) { book(child.id, EntryKind.TASK, 1, "2026-10-${10 + it}T10:00:00Z") }
            fixture.badges.evaluate(child.id)

            val overview = fixture.badges.overview(child, child.id)

            val tenTasks = overview.items.single { it.badge == Badge.TASKS_10 }
            assertEquals(7, tenTasks.progress)
            assertNull(tenTasks.awardedAt)
            assertNotNull(overview.items.single { it.badge == Badge.FIRST_TASK }.awardedAt)
            assertEquals(Badge.entries.size, overview.totalCount)
            assertEquals(Badge.entries.size, overview.items.size)
            assertEquals(2, overview.earnedCount)
        }

    @Test
    fun `REQ-GAME-021 secret badge is masked until earned`() =
        runTest {
            val child = fixture.child()

            val before =
                fixture.badges
                    .overview(child, child.id)
                    .items
                    .single { it.badge == Badge.POINTS_1000 }
            book(child.id, EntryKind.MANUAL, 1000)
            fixture.badges.evaluate(child.id)
            val after =
                fixture.badges
                    .overview(child, child.id)
                    .items
                    .single { it.badge == Badge.POINTS_1000 }

            assertTrue(before.masked)
            assertNull(before.progress)
            assertNotNull(Badge.POINTS_1000.hint)
            assertFalse(after.masked)
            assertNotNull(after.awardedAt)
        }

    @Test
    fun `REQ-GAME-021 sibling sees only earned badges`() =
        runTest {
            val owner = fixture.child()
            val sibling = fixture.child()
            book(owner.id, EntryKind.MANUAL, 1000)
            fixture.badges.evaluate(owner.id)

            val overview = fixture.badges.overview(sibling, owner.id)

            assertEquals(listOf("points-100", "points-500", "points-1000"), overview.items.map { it.badge.id })
            assertTrue(overview.items.all { it.awardedAt != null && it.progress == null && !it.masked })
            assertEquals(Badge.entries.size, overview.totalCount)
        }

    @Test
    fun `REQ-GAME-021 parent sees full overview`() =
        runTest {
            val child = fixture.child()
            val parent = fixture.parent()

            val overview = fixture.badges.overview(parent, child.id)

            assertEquals(Badge.entries.size, overview.items.size)
            assertEquals(0, overview.earnedCount)
            assertEquals(0, overview.items.single { it.badge == Badge.FIRST_TASK }.progress)
        }

    @Test
    fun `REQ-GAME-021 unknown member 404, parent member 400`() =
        runTest {
            val parent = fixture.parent()

            assertFailsWith<AppException.NotFound> { fixture.badges.overview(parent, MemberId("missing")) }
            assertFailsWith<AppException.Validation> { fixture.badges.overview(parent, parent.id) }
        }

    @Test
    fun `REQ-GAME-020 unknown stored badge ids are not counted`() =
        runTest {
            val child = fixture.child()
            fixture.badgeRepository.award(child.id, "retired-badge", Instant.parse("2026-10-24T10:00:00Z"))

            assertEquals(0, fixture.badges.overview(child, child.id).earnedCount)
        }
}
