package de.familyplanner.tasks.persistence

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.tasks.catalog.CatalogTask
import de.familyplanner.tasks.catalog.CatalogTaskId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedCatalogRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private lateinit var repository: ExposedCatalogRepository

    @BeforeTest
    fun setUp() {
        repository = ExposedCatalogRepository(DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now)))
    }

    private fun task(
        id: String,
        title: String,
        points: Int = 1,
    ) = CatalogTask(CatalogTaskId(id), title, null, points, archived = false)

    @Test
    fun `REQ-TASK-010 created task can be read back`() =
        runTest {
            val created = task("a", "Bad putzen", 3).copy(emoji = "🛁")

            repository.create(created, now)

            assertEquals(created, repository.findById(created.id))
            assertNull(repository.findById(CatalogTaskId("missing")))
        }

    @Test
    fun `REQ-TASK-010 list is sorted case-insensitive and hides archived by default`() =
        runTest {
            listOf(task("1", "bad"), task("2", "Äpfel"), task("3", "Zimmer"), task("4", "Abwasch")).forEach { repository.create(it, now) }
            repository.archive(CatalogTaskId("2"), now)

            assertEquals(listOf("Abwasch", "bad", "Zimmer"), repository.findAll(includeArchived = false).map { it.title })
            assertEquals(4, repository.findAll(includeArchived = true).size)
        }

    @Test
    fun `REQ-TASK-010 update changes active tasks only`() =
        runTest {
            val original = task("a", "Alt")
            repository.create(original, now)

            assertTrue(repository.update(original.copy(title = "Neu", points = 5), now))
            assertEquals("Neu", repository.findById(original.id)?.title)

            repository.archive(original.id, now)
            assertFalse(repository.update(original.copy(title = "Spät"), now))
            assertEquals("Neu", repository.findById(original.id)?.title)
            assertFalse(repository.update(task("missing", "x"), now))
        }

    @Test
    fun `REQ-TASK-010 archive is idempotent and false for unknown tasks`() =
        runTest {
            repository.create(task("a", "A"), now)

            assertTrue(repository.archive(CatalogTaskId("a"), now))
            assertTrue(repository.archive(CatalogTaskId("a"), now))
            assertFalse(repository.archive(CatalogTaskId("missing"), now))
            assertTrue(repository.findById(CatalogTaskId("a"))?.archived == true)
        }

    @Test
    fun `REQ-TASK-020 findActiveByIds skips archived and unknown tasks`() =
        runTest {
            repository.create(task("a", "A"), now)
            repository.create(task("b", "B"), now)
            repository.archive(CatalogTaskId("b"), now)

            val found = repository.findActiveByIds(listOf("a", "b", "missing").map { CatalogTaskId(it) })

            assertEquals(listOf("a"), found.map { it.id.value })
        }
}
