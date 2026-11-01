package de.familyplanner.tasks.board

import de.familyplanner.core.error.AppException
import de.familyplanner.tasks.catalog.CatalogTaskId
import de.familyplanner.tasks.fakes.TasksFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoolServiceTest {
    private val fixture = TasksFixture()
    private val pool = fixture.pool

    @Test
    fun `REQ-TASK-028 child takes a pool task as a todo card`() =
        runTest {
            val child = fixture.child()
            val task = fixture.catalogTask(points = 3, title = "Bad putzen", emoji = "🛁", childSelectable = true)

            val card = pool.take(child, task.id, alreadyDone = false)

            assertEquals(card, fixture.cardRepository.cards[card.id])
            assertEquals(Triple("Bad putzen", "🛁", 3), Triple(card.title, card.emoji, card.points))
            assertEquals(Triple(TaskStatus.TODO, child.id, task.id), Triple(card.status, card.memberId, card.catalogTaskId))
            assertTrue(card.selfAssigned)
            assertNull(card.dueAt)
            assertNull(card.reportedAt)
        }

    @Test
    fun `REQ-TASK-028 already done goes straight to review`() =
        runTest {
            val card = pool.take(fixture.child(), fixture.catalogTask(childSelectable = true).id, alreadyDone = true)

            assertEquals(TaskStatus.REVIEW, card.status)
            assertEquals(fixture.clock.instant(), card.reportedAt)
            assertTrue(card.selfAssigned)
        }

    @Test
    fun `REQ-TASK-028 the same task can be taken repeatedly`() =
        runTest {
            val child = fixture.child()
            val task = fixture.catalogTask(childSelectable = true)

            pool.take(child, task.id, alreadyDone = false)
            pool.take(child, task.id, alreadyDone = false)

            assertEquals(2, fixture.cardRepository.cards.size)
        }

    @Test
    fun `REQ-TASK-028 a parent cannot take pool tasks`() =
        runTest {
            val task = fixture.catalogTask(childSelectable = true)

            assertFailsWith<AppException.Forbidden> { pool.take(fixture.parent(), task.id, alreadyDone = false) }
            assertEquals(emptyMap(), fixture.cardRepository.cards)
        }

    @Test
    fun `REQ-TASK-028 tasks that are not released cannot be taken`() =
        runTest {
            val task = fixture.catalogTask()

            assertFailsWith<AppException.Forbidden> { pool.take(fixture.child(), task.id, alreadyDone = false) }
        }

    @Test
    fun `REQ-TASK-028 archived tasks cannot be taken`() =
        runTest {
            val task = fixture.catalogTask(childSelectable = true).also { fixture.catalog.archive(it.id) }

            assertFailsWith<AppException.Conflict> { pool.take(fixture.child(), task.id, alreadyDone = false) }
        }

    @Test
    fun `REQ-TASK-028 unknown tasks are not found`() =
        runTest {
            assertFailsWith<AppException.NotFound> { pool.take(fixture.child(), CatalogTaskId("missing"), alreadyDone = false) }
        }
}
