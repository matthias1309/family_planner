package de.familyplanner.tasks.board

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskTransitionsTest {
    private val childA = Actor(MemberId("a"), Role.CHILD)
    private val childB = Actor(MemberId("b"), Role.CHILD)
    private val parent = Actor(MemberId("p"), Role.PARENT)
    private val cardOfA = card(MemberId("a"))

    private fun card(owner: MemberId) =
        TaskCard(TaskCardId("c"), owner, null, "t", null, 1, TaskStatus.TODO, null, Instant.EPOCH, null, null)

    @Test
    fun `REQ-TASK-022 child may report and withdraw only own cards`() {
        listOf(TaskTransition.REPORT, TaskTransition.WITHDRAW).forEach {
            assertTrue(it.isAllowedFor(childA, cardOfA))
            assertFalse(it.isAllowedFor(childB, cardOfA))
            assertFalse(it.isAllowedFor(parent, cardOfA))
        }
    }

    @Test
    fun `REQ-TASK-023 only parents accept and reject`() {
        listOf(TaskTransition.ACCEPT, TaskTransition.REJECT).forEach {
            assertTrue(it.isAllowedFor(parent, cardOfA))
            assertFalse(it.isAllowedFor(childA, cardOfA))
        }
    }

    @Test
    fun `REQ-TASK-023 transitions start only from allowed statuses`() {
        assertEquals(setOf(TaskStatus.TODO), TaskTransition.REPORT.from())
        assertEquals(setOf(TaskStatus.REVIEW), TaskTransition.WITHDRAW.from())
        assertEquals(setOf(TaskStatus.REVIEW, TaskStatus.TODO), TaskTransition.ACCEPT.from())
        assertEquals(setOf(TaskStatus.REVIEW), TaskTransition.REJECT.from())
        assertEquals(TaskStatus.REVIEW, TaskTransition.REPORT.to())
        assertEquals(TaskStatus.TODO, TaskTransition.WITHDRAW.to())
        assertEquals(TaskStatus.TODO, TaskTransition.REJECT.to())
        assertEquals(TaskStatus.DONE, TaskTransition.ACCEPT.to())
    }

    @Test
    fun `REQ-TASK-023 done is final`() {
        TaskTransition.entries.forEach { assertFalse(TaskStatus.DONE in it.from()) }
        assertFalse(TaskStatus.DONE.isRemovable())
        assertTrue(TaskStatus.REVIEW.isRemovable())
    }
}
