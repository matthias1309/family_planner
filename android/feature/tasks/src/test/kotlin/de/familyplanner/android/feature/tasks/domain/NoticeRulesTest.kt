package de.familyplanner.android.feature.tasks.domain

import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.domain.notice.TaskNotice
import de.familyplanner.android.feature.tasks.domain.notice.taskNotices
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.lane
import kotlin.test.Test
import kotlin.test.assertEquals

class NoticeRulesTest {
    private fun ben(vararg cards: de.familyplanner.android.feature.tasks.domain.board.TaskCard) = lane("c-1", "Ben", *cards)

    @Test
    fun `REQ-TASK-030 first sync only records a baseline`() {
        val now = listOf(ben(card("a", status = TaskStatus.REVIEW)))

        assertEquals(emptyList(), taskNotices(isParent = true, memberId = "p-1", previous = null, current = now))
        assertEquals(emptyList(), taskNotices(isParent = false, memberId = "c-1", previous = null, current = now))
    }

    @Test
    fun `REQ-TASK-030 parents hear about newly reported cards once`() {
        val before = listOf(ben(card("a")))
        val after = listOf(ben(card("a", title = "Bad putzen", status = TaskStatus.REVIEW)))

        assertEquals(listOf(TaskNotice.Reported("a", "Bad putzen", "Ben")), taskNotices(true, "p-1", before, after))
        assertEquals(emptyList(), taskNotices(true, "p-1", after, after))
    }

    @Test
    fun `REQ-TASK-030 parents hear about a card that is new and already reported`() {
        val after = listOf(ben(card("a", status = TaskStatus.REVIEW)))

        assertEquals(1, taskNotices(true, "p-1", emptyList(), after).size)
    }

    @Test
    fun `REQ-TASK-030 child hears about new, accepted and rejected own cards`() {
        val before =
            listOf(ben(card("r", status = TaskStatus.REVIEW), card("t", status = TaskStatus.TODO), card("x", status = TaskStatus.REVIEW)))
        val after =
            listOf(
                ben(
                    card("n", title = "Neu"),
                    card("r", title = "Bad", status = TaskStatus.DONE, points = 3),
                    card("t", status = TaskStatus.DONE, points = 1),
                    card("x", title = "Zimmer", status = TaskStatus.TODO, reason = "Unter dem Bett"),
                ),
            )

        val notices = taskNotices(false, "c-1", before, after)

        assertEquals(
            setOf(
                TaskNotice.Assigned("n", "Neu"),
                TaskNotice.Accepted("r", "Bad", 3),
                TaskNotice.Accepted("t", "Aufgabe t", 1),
                TaskNotice.Rejected("x", "Zimmer", "Unter dem Bett"),
            ),
            notices.toSet(),
        )
    }

    @Test
    fun `REQ-TASK-030 child hears nothing about siblings or own reports`() {
        val before = listOf(ben(card("a")), lane("c-2", "Lea", card("l", "c-2")))
        val after =
            listOf(
                ben(card("a", status = TaskStatus.REVIEW)),
                lane("c-2", "Lea", card("l", "c-2", status = TaskStatus.DONE), card("m", "c-2")),
            )

        assertEquals(emptyList(), taskNotices(false, "c-1", before, after))
    }

    @Test
    fun `REQ-TASK-030 removed cards are not announced`() {
        val before = listOf(ben(card("a", status = TaskStatus.REVIEW)))

        assertEquals(emptyList(), taskNotices(true, "p-1", before, listOf(ben())))
        assertEquals(emptyList(), taskNotices(false, "c-1", before, listOf(ben())))
    }
}
