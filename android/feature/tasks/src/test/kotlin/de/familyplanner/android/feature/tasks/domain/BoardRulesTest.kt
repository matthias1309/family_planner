package de.familyplanner.android.feature.tasks.domain

import de.familyplanner.android.feature.tasks.domain.board.CardAction
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.domain.board.cardActions
import de.familyplanner.android.feature.tasks.domain.board.orderedLanes
import de.familyplanner.android.feature.tasks.domain.board.removing
import de.familyplanner.android.feature.tasks.domain.board.replacing
import de.familyplanner.android.feature.tasks.domain.board.reviewCount
import de.familyplanner.android.feature.tasks.domain.board.reviewQueue
import de.familyplanner.android.feature.tasks.domain.board.sections
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.lane
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class BoardRulesTest {
    private val todo = card("t", status = TaskStatus.TODO)
    private val review = card("r", status = TaskStatus.REVIEW)
    private val done = card("d", status = TaskStatus.DONE)

    @Test
    fun `REQ-TASK-022 child sees report on own todo and withdraw on own review`() {
        assertEquals(listOf(CardAction.REPORT), cardActions(todo, "c-1", isParent = false, isOffline = false))
        assertEquals(listOf(CardAction.WITHDRAW), cardActions(review, "c-1", isParent = false, isOffline = false))
        assertEquals(emptyList(), cardActions(done, "c-1", isParent = false, isOffline = false))
    }

    @Test
    fun `REQ-TASK-022 child sees no actions on a sibling's card`() {
        assertEquals(emptyList(), cardActions(todo, "c-2", isParent = false, isOffline = false))
        assertEquals(emptyList(), cardActions(review, "c-2", isParent = false, isOffline = false))
    }

    @Test
    fun `REQ-TASK-023 parent sees accept, reject and remove on review cards`() {
        assertEquals(
            listOf(CardAction.ACCEPT, CardAction.REJECT, CardAction.REMOVE),
            cardActions(review, "p-1", isParent = true, isOffline = false),
        )
    }

    @Test
    fun `REQ-TASK-023 parent can accept a todo card directly or remove it`() {
        assertEquals(
            listOf(CardAction.ACCEPT_DIRECTLY, CardAction.REMOVE),
            cardActions(todo, "p-1", isParent = true, isOffline = false),
        )
    }

    @Test
    fun `REQ-TASK-023 done cards and offline mode have no actions`() {
        assertEquals(emptyList(), cardActions(done, "p-1", isParent = true, isOffline = false))
        assertEquals(emptyList(), cardActions(review, "p-1", isParent = true, isOffline = true))
        assertEquals(emptyList(), cardActions(todo, "c-1", isParent = false, isOffline = true))
    }

    @Test
    fun `REQ-TASK-021 sections sort todo and review oldest first, done newest first`() {
        val at = { hour: Int -> Instant.parse("2026-10-24T%02d:00:00Z".format(hour)) }
        val cards =
            listOf(
                card("t2", assignedAt = at(9)),
                card("t1", assignedAt = at(8)),
                card("r2", status = TaskStatus.REVIEW, reportedAt = at(12)),
                card("r1", status = TaskStatus.REVIEW, reportedAt = at(11)),
                card("d1", status = TaskStatus.DONE, doneAt = at(10)),
                card("d2", status = TaskStatus.DONE, doneAt = at(13)),
            )

        val result = sections(cards)

        assertEquals(listOf("t1", "t2"), result.todo.map { it.id })
        assertEquals(listOf("r1", "r2"), result.review.map { it.id })
        assertEquals(listOf("d2", "d1"), result.done.map { it.id })
    }

    @Test
    fun `REQ-TASK-021 review queue spans all children oldest report first`() {
        val at = { hour: Int -> Instant.parse("2026-10-24T%02d:00:00Z".format(hour)) }
        val lanes =
            listOf(
                lane("c-1", "Ben", card("b1", status = TaskStatus.REVIEW, reportedAt = at(12)), card("b2")),
                lane("c-2", "Lea", card("l1", "c-2", status = TaskStatus.REVIEW, reportedAt = at(9))),
            )

        val queue = reviewQueue(lanes)

        assertEquals(listOf("l1", "b1"), queue.map { it.second.id })
        assertEquals(listOf("Lea", "Ben"), queue.map { it.first.name })
        assertEquals(2, reviewCount(lanes))
    }

    @Test
    fun `REQ-TASK-021 a child sees the own lane first`() {
        val lanes = listOf(lane("c-1", "Ben"), lane("c-2", "Lea"), lane("c-3", "Mia"))

        assertEquals(listOf("Mia", "Ben", "Lea"), orderedLanes(lanes, "c-3", isParent = false).map { it.name })
        assertEquals(listOf("Ben", "Lea", "Mia"), orderedLanes(lanes, "p-1", isParent = true).map { it.name })
    }

    @Test
    fun `REQ-TASK-022 replacing and removing keep the other cards`() {
        val lanes = listOf(lane("c-1", "Ben", card("a"), card("b")), lane("c-2", "Lea", card("c", "c-2")))

        val moved = lanes.replacing(card("a", status = TaskStatus.REVIEW))
        val removed = lanes.removing("b")

        assertEquals(TaskStatus.REVIEW, moved[0].cards.first { it.id == "a" }.status)
        assertEquals(listOf("a"), removed[0].cards.map { it.id })
        assertEquals(listOf("c"), removed[1].cards.map { it.id })
    }
}
