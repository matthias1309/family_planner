package de.familyplanner.tasks.board

import de.familyplanner.core.error.AppException
import de.familyplanner.core.events.TaskAccepted
import de.familyplanner.core.member.MemberId
import de.familyplanner.tasks.catalog.CatalogTaskId
import de.familyplanner.tasks.catalog.CatalogTaskInput
import de.familyplanner.tasks.fakes.TasksFixture
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BoardServiceTest {
    private val fixture = TasksFixture()
    private val board = fixture.board

    private suspend fun assignOne(
        child: Actor,
        points: Int = 2,
    ): TaskCard = board.assign(child.id, listOf(fixture.catalogTask(points).id)).single()

    private suspend fun reported(child: Actor): TaskCard = assignOne(child).also { board.report(child, it.id) }

    @Test
    fun `REQ-TASK-020 assigning copies title, emoji and points`() =
        runTest {
            val child = fixture.child()
            val task = fixture.catalogTask(points = 2, title = "Bad putzen", emoji = "🛁")

            val card = board.assign(child.id, listOf(task.id)).single()
            fixture.catalog.update(task.id, CatalogTaskInput("Anders", null, 3))

            val stored = checkNotNull(fixture.cardRepository.cards[card.id])
            assertEquals(Triple("Bad putzen", "🛁", 2), Triple(stored.title, stored.emoji, stored.points))
            assertEquals(TaskStatus.TODO, stored.status)
            assertEquals(task.id, stored.catalogTaskId)
        }

    @Test
    fun `REQ-TASK-020 same task twice gives two cards`() =
        runTest {
            val child = fixture.child()
            val task = fixture.catalogTask()

            val cards = board.assign(child.id, listOf(task.id, task.id))

            assertEquals(2, cards.size)
            assertEquals(2, cards.map { it.id }.distinct().size)
        }

    @Test
    fun `REQ-TASK-020 only children get tasks`() =
        runTest {
            val parent = fixture.parent()
            val task = fixture.catalogTask()

            assertFailsWith<AppException.Validation> { board.assign(parent.id, listOf(task.id)) }
            assertFailsWith<AppException.NotFound> { board.assign(MemberId("missing"), listOf(task.id)) }
        }

    @Test
    fun `REQ-TASK-020 archived or unknown catalog task is rejected`() =
        runTest {
            val child = fixture.child()
            val archived = fixture.catalogTask().also { fixture.catalog.archive(it.id) }

            assertFailsWith<AppException.Validation> { board.assign(child.id, listOf(archived.id)) }
            assertFailsWith<AppException.Validation> { board.assign(child.id, listOf(CatalogTaskId("missing"))) }
            assertEquals(0, fixture.cardRepository.cards.size)
        }

    @Test
    fun `REQ-TASK-020 between 1 and 20 tasks per call`() =
        runTest {
            val child = fixture.child()
            val task = fixture.catalogTask()

            assertFailsWith<AppException.Validation> { board.assign(child.id, emptyList()) }
            assertFailsWith<AppException.Validation> { board.assign(child.id, List(ASSIGN_MAX + 1) { task.id }) }
            assertEquals(ASSIGN_MAX, board.assign(child.id, List(ASSIGN_MAX) { task.id }).size)
        }

    @Test
    fun `REQ-TASK-021 board has one lane per child sorted by name, also empty`() =
        runTest {
            val parent = fixture.parent()
            val zoe = fixture.child("Zoe")
            fixture.child("Anna")
            assignOne(zoe)

            val lanes = board.board()

            assertEquals(listOf("Anna", "Zoe"), lanes.map { it.member.name })
            assertEquals(listOf(0, 1), lanes.map { it.cards.size })
            assertEquals(false, lanes.any { it.member.id == parent.id })
        }

    @Test
    fun `REQ-TASK-021 lane orders todo, review, then done newest first`() =
        runTest {
            val child = fixture.child()
            val doneOld = assignOne(child).also { board.accept(fixture.parent(), it.id) }
            fixture.clock.advance(Duration.ofHours(1))
            val doneNew = assignOne(child).also { board.accept(fixture.parent(), it.id) }
            val review = reported(child)
            val todo = assignOne(child)

            val ids =
                board
                    .board()
                    .single()
                    .cards
                    .map { it.id }

            assertEquals(listOf(todo.id, review.id, doneNew.id, doneOld.id), ids)
        }

    @Test
    fun `REQ-TASK-025 done cards older than 7 days leave the board but stay in history`() =
        runTest {
            val child = fixture.child()
            val parent = fixture.parent()
            val old = assignOne(child).also { board.accept(parent, it.id) }
            fixture.clock.advance(Duration.ofDays(2))
            val recent = assignOne(child).also { board.accept(parent, it.id) }
            fixture.clock.advance(Duration.ofDays(DONE_VISIBLE_DAYS - 1))

            assertEquals(
                listOf(recent.id),
                board
                    .board()
                    .single()
                    .cards
                    .map { it.id },
            )
            assertEquals(listOf(recent.id, old.id), board.history(child.id, HISTORY_LIMIT_DEFAULT).map { it.id })
        }

    @Test
    fun `REQ-TASK-021 history limit must be 1 to 100`() =
        runTest {
            val child = fixture.child()

            assertFailsWith<AppException.Validation> { board.history(child.id, 0) }
            assertFailsWith<AppException.Validation> { board.history(child.id, HISTORY_LIMIT_MAX + 1) }
            assertFailsWith<AppException.NotFound> { board.history(MemberId("missing"), 1) }
            assertEquals(emptyList(), board.history(child.id, HISTORY_LIMIT_MAX))
        }

    @Test
    fun `REQ-TASK-022 child reports own card and withdraws it`() =
        runTest {
            val child = fixture.child()
            val card = assignOne(child)

            val reportedCard = board.report(child, card.id)
            assertEquals(TaskStatus.REVIEW, reportedCard.status)
            assertEquals(fixture.clock.instant(), reportedCard.reportedAt)

            val withdrawn = board.withdraw(child, card.id)
            assertEquals(TaskStatus.TODO, withdrawn.status)
            assertNull(withdrawn.reportedAt)
        }

    @Test
    fun `REQ-TASK-022 child cannot move a sibling's card`() =
        runTest {
            val sibling = fixture.child()
            val card = assignOne(fixture.child())

            assertFailsWith<AppException.Forbidden> { board.report(sibling, card.id) }
            assertFailsWith<AppException.Forbidden> { board.withdraw(sibling, card.id) }
        }

    @Test
    fun `REQ-TASK-022 child cannot accept or reject and unknown card is not found`() =
        runTest {
            val child = fixture.child()
            val card = reported(child)

            assertFailsWith<AppException.Forbidden> { board.accept(child, card.id) }
            assertFailsWith<AppException.Forbidden> { board.reject(child, card.id, null) }
            assertFailsWith<AppException.NotFound> { board.report(child, TaskCardId("missing")) }
        }

    @Test
    fun `REQ-TASK-022 report and withdraw only from the right status`() =
        runTest {
            val child = fixture.child()
            val card = assignOne(child)

            assertFailsWith<AppException.Conflict> { board.withdraw(child, card.id) }
            board.report(child, card.id)
            assertFailsWith<AppException.Conflict> { board.report(child, card.id) }
        }

    @Test
    fun `REQ-TASK-023 parent accepts reported card and an event is recorded`() =
        runTest {
            val child = fixture.child()
            val parent = fixture.parent()
            val card = reported(child)

            val accepted = board.accept(parent, card.id)

            assertEquals(TaskStatus.DONE, accepted.status)
            assertEquals(fixture.clock.instant(), accepted.doneAt)
            val expected =
                TaskAccepted(card.id.value, child.id.value, card.title, 2, parent.id.value, fixture.clock.instant().toEpochMilli())
            assertEquals(listOf(expected), fixture.cardRepository.recordedEvents)
            assertEquals(1, fixture.notifier.calls)
        }

    @Test
    fun `REQ-TASK-023 parent accepts a todo card directly`() =
        runTest {
            val card = assignOne(fixture.child())

            assertEquals(TaskStatus.DONE, board.accept(fixture.parent(), card.id).status)
            assertEquals(1, fixture.cardRepository.recordedEvents.size)
        }

    @Test
    fun `REQ-TASK-023 parent rejects with reason, child sees it, reporting clears it`() =
        runTest {
            val child = fixture.child()
            val card = reported(child)

            val rejected = board.reject(fixture.parent(), card.id, " nicht sauber ")
            assertEquals(TaskStatus.TODO, rejected.status)
            assertEquals("nicht sauber", rejected.rejectionReason)

            assertNull(board.report(child, card.id).rejectionReason)
            assertEquals(0, fixture.notifier.calls)
        }

    @Test
    fun `REQ-TASK-023 rejection without reason is allowed, too long reason is not`() =
        runTest {
            val child = fixture.child()
            val parent = fixture.parent()

            listOf(null, "  ").forEach { reason ->
                val card = reported(child)
                assertNull(board.reject(parent, card.id, reason).rejectionReason)
            }
            assertFailsWith<AppException.Validation> { board.reject(parent, reported(child).id, "x".repeat(REASON_MAX + 1)) }
        }

    @Test
    fun `REQ-TASK-023 done cards cannot move`() =
        runTest {
            val child = fixture.child()
            val parent = fixture.parent()
            val card = assignOne(child).also { board.accept(parent, it.id) }

            assertFailsWith<AppException.Conflict> { board.accept(parent, card.id) }
            assertFailsWith<AppException.Conflict> { board.reject(parent, card.id, null) }
            assertFailsWith<AppException.Conflict> { board.report(child, card.id) }
            assertFailsWith<AppException.Conflict> { board.withdraw(child, card.id) }
            assertEquals(1, fixture.cardRepository.recordedEvents.size)
        }

    @Test
    fun `REQ-TASK-023 a failing delivery does not fail the acceptance`() =
        runTest {
            val failing = BoardService(fixture.members, fixture.catalogRepository, fixture.cardRepository, { error("boom") }, fixture.clock)
            val card = assignOne(fixture.child())

            assertEquals(TaskStatus.DONE, failing.accept(fixture.parent(), card.id).status)
        }

    @Test
    fun `REQ-TASK-024 parent removes todo or review card, not done`() =
        runTest {
            val child = fixture.child()
            val todo = assignOne(child)
            val review = reported(child)
            val done = assignOne(child).also { board.accept(fixture.parent(), it.id) }

            board.remove(todo.id)
            board.remove(review.id)

            assertEquals(
                listOf(done.id),
                fixture.cardRepository.cards.keys
                    .toList(),
            )
            assertFailsWith<AppException.Conflict> { board.remove(done.id) }
            assertFailsWith<AppException.NotFound> { board.remove(todo.id) }
        }
}
