package de.familyplanner.tasks.catalog

import de.familyplanner.core.error.AppException
import de.familyplanner.tasks.fakes.TasksFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogServiceTest {
    private val fixture = TasksFixture()
    private val service = fixture.catalog

    @Test
    fun `REQ-TASK-010 parent creates catalog task with trimmed title`() =
        runTest {
            val task = service.create(CatalogTaskInput(" Zimmer saugen ", "🧹", 2))

            assertEquals("Zimmer saugen", task.title)
            assertEquals(2, task.points)
            assertEquals(listOf(task), service.list(includeArchived = false))
        }

    @Test
    fun `REQ-TASK-010 title, emoji and points are validated`() =
        runTest {
            val invalid =
                listOf(
                    CatalogTaskInput("", null, 1),
                    CatalogTaskInput("x".repeat(TASK_TITLE_MAX + 1), null, 1),
                    CatalogTaskInput("ok", "e".repeat(TASK_EMOJI_MAX + 1), 1),
                    CatalogTaskInput("ok", null, 0),
                    CatalogTaskInput("ok", null, TASK_POINTS_MAX + 1),
                )

            invalid.forEach { assertFailsWith<AppException.Validation> { service.create(it) } }
        }

    @Test
    fun `REQ-TASK-010 limits themselves are accepted`() =
        runTest {
            service.create(CatalogTaskInput("x".repeat(TASK_TITLE_MAX), "e".repeat(TASK_EMOJI_MAX), TASK_POINTS_MAX))
            service.create(CatalogTaskInput("y", null, TASK_POINTS_MIN))

            assertEquals(2, service.list(includeArchived = false).size)
        }

    @Test
    fun `REQ-TASK-010 empty emoji is stored as none`() =
        runTest {
            assertNull(service.create(CatalogTaskInput("Bad putzen", "  ", 3)).emoji)
        }

    @Test
    fun `REQ-TASK-010 update changes an active task`() =
        runTest {
            val task = fixture.catalogTask(points = 2)

            val updated = service.update(task.id, CatalogTaskInput("Neu", null, 5))

            assertEquals(CatalogTask(task.id, "Neu", null, 5, archived = false), updated)
            assertEquals(updated, fixture.catalogRepository.tasks[task.id])
        }

    @Test
    fun `REQ-TASK-010 archived task is hidden and cannot be edited`() =
        runTest {
            val task = fixture.catalogTask()

            service.archive(task.id)

            assertTrue(service.list(includeArchived = false).isEmpty())
            assertTrue(service.list(includeArchived = true).single().archived)
            assertFailsWith<AppException.Conflict> { service.update(task.id, CatalogTaskInput("x", null, 1)) }
        }

    @Test
    fun `REQ-TASK-010 archiving twice is fine, unknown task is not found`() =
        runTest {
            val task = fixture.catalogTask()
            service.archive(task.id)

            service.archive(task.id)

            assertFailsWith<AppException.NotFound> { service.archive(CatalogTaskId("missing")) }
            assertFailsWith<AppException.NotFound> { service.update(CatalogTaskId("missing"), CatalogTaskInput("x", null, 1)) }
        }

    @Test
    fun `REQ-TASK-010 update is a conflict when the task was archived meanwhile`() =
        runTest {
            val task = fixture.catalogTask()
            fixture.catalogRepository.tasks[task.id] = task.copy(archived = true)

            assertFalse(fixture.catalogRepository.update(task, fixture.clock.instant()))
        }
}
