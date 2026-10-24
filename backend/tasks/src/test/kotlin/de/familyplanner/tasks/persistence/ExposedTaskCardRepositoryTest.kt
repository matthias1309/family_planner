package de.familyplanner.tasks.persistence

import de.familyplanner.core.events.DomainEventDispatcher
import de.familyplanner.core.events.StoredEvent
import de.familyplanner.core.events.TaskAccepted
import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.core.persistence.DomainEventsTable
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.tasks.board.TaskCard
import de.familyplanner.tasks.board.TaskCardId
import de.familyplanner.tasks.board.TaskStatus
import de.familyplanner.tasks.board.TaskTransition
import de.familyplanner.tasks.board.TransitionOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedTaskCardRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private lateinit var db: Database
    private lateinit var repository: ExposedTaskCardRepository
    private var childId = MemberId("unset")
    private var parentId = MemberId("unset")

    @BeforeTest
    fun setUp() =
        runTest {
            db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))
            val members = ExposedMemberRepository(db)
            parentId = members.create(aMember(role = Role.PARENT)).id
            childId = members.create(aMember(role = Role.CHILD)).id
            repository = ExposedTaskCardRepository(db)
        }

    private fun card(
        id: String,
        status: TaskStatus = TaskStatus.TODO,
        points: Int = 3,
        doneAt: Instant? = null,
    ) = TaskCard(TaskCardId(id), childId, null, "Bad putzen", "🛁", points, status, null, now, null, doneAt)

    private suspend fun events() =
        dbQuery(db) {
            DomainEventsTable.selectAll().map {
                it[DomainEventsTable.type] to
                    it[DomainEventsTable.payload]
            }
        }

    private suspend fun transition(
        id: String,
        transition: TaskTransition,
        reason: String? = null,
        at: Instant = now,
    ) = repository.transition(TaskCardId(id), transition, parentId, reason, at)

    @Test
    fun `cards round-trip through the database`() =
        runTest {
            repository.createAll(listOf(card("a")))

            assertEquals(card("a"), repository.findById(TaskCardId("a")))
            assertNull(repository.findById(TaskCardId("missing")))
        }

    @Test
    fun `REQ-TASK-022 report, withdraw and reject update timestamps and reason`() =
        runTest {
            repository.createAll(listOf(card("a")))

            assertEquals(TransitionOutcome.CHANGED, transition("a", TaskTransition.REPORT, at = now.plusSeconds(1)))
            assertEquals(now.plusSeconds(1), repository.findById(TaskCardId("a"))?.reportedAt)
            assertEquals(TransitionOutcome.CHANGED, transition("a", TaskTransition.REJECT, "nicht sauber"))
            repository.findById(TaskCardId("a"))!!.let {
                assertEquals(TaskStatus.TODO, it.status)
                assertEquals("nicht sauber", it.rejectionReason)
                assertNull(it.reportedAt)
            }
            transition("a", TaskTransition.REPORT)
            assertNull(repository.findById(TaskCardId("a"))?.rejectionReason)
            transition("a", TaskTransition.WITHDRAW)
            assertEquals(TaskStatus.TODO, repository.findById(TaskCardId("a"))?.status)
        }

    @Test
    fun `transition reports wrong status and unknown cards`() =
        runTest {
            repository.createAll(listOf(card("a")))

            assertEquals(TransitionOutcome.WRONG_STATUS, transition("a", TaskTransition.WITHDRAW))
            assertEquals(TransitionOutcome.NOT_FOUND, transition("missing", TaskTransition.REPORT))
            assertTrue(events().isEmpty())
        }

    @Test
    fun `REQ-TASK-023 accepting writes the card and the event atomically`() =
        runTest {
            repository.createAll(listOf(card("a", points = 3)))
            transition("a", TaskTransition.REPORT)

            assertEquals(TransitionOutcome.CHANGED, transition("a", TaskTransition.ACCEPT, at = now.plusSeconds(5)))

            val done = repository.findById(TaskCardId("a"))!!
            assertEquals(TaskStatus.DONE, done.status)
            assertEquals(now.plusSeconds(5), done.doneAt)
            val (type, payload) = events().single()
            assertEquals("task.accepted.v1", type)
            assertTrue("\"points\":3" in payload, payload)
        }

    @Test
    fun `REQ-TASK-023 accepted event reaches a subscriber`() =
        runTest {
            repository.createAll(listOf(card("a", points = 3)))
            transition("a", TaskTransition.ACCEPT)
            val seen = mutableListOf<StoredEvent>()
            val dispatcher = DomainEventDispatcher(db)
            dispatcher.subscribe("test") { seen += it }

            dispatcher.dispatchPending()

            val event = seen.single().event as TaskAccepted
            assertEquals(TaskAccepted("a", childId.value, "Bad putzen", 3, parentId.value, now.toEpochMilli()), event)
        }

    @Test
    fun `REQ-TASK-023 concurrent accepts record exactly one event`() =
        runTest {
            repository.createAll(listOf(card("a")))
            transition("a", TaskTransition.REPORT)

            val outcomes =
                listOf(
                    async { transition("a", TaskTransition.ACCEPT) },
                    async { transition("a", TaskTransition.ACCEPT) },
                ).awaitAll()

            assertEquals(setOf(TransitionOutcome.CHANGED, TransitionOutcome.WRONG_STATUS), outcomes.toSet())
            assertEquals(1, events().size)
        }

    @Test
    fun `REQ-TASK-025 board query keeps open cards and recent done cards`() =
        runTest {
            repository.createAll(
                listOf(
                    card("todo"),
                    card("review", TaskStatus.REVIEW),
                    card("old", TaskStatus.DONE, doneAt = now.minus(Duration.ofDays(8))),
                    card("recent", TaskStatus.DONE, doneAt = now.minus(Duration.ofDays(6))),
                ),
            )

            val ids = repository.findBoard(now.minus(Duration.ofDays(7))).map { it.id.value }.toSet()

            assertEquals(setOf("todo", "review", "recent"), ids)
        }

    @Test
    fun `REQ-TASK-025 history lists done cards newest first with a limit`() =
        runTest {
            repository.createAll(
                listOf(card("a", TaskStatus.DONE, doneAt = now), card("b", TaskStatus.DONE, doneAt = now.plusSeconds(10)), card("c")),
            )

            assertEquals(listOf("b", "a"), repository.findDone(childId, 10).map { it.id.value })
            assertEquals(listOf("b"), repository.findDone(childId, 1).map { it.id.value })
        }

    @Test
    fun `REQ-TASK-024 remove deletes open cards but not done cards`() =
        runTest {
            repository.createAll(listOf(card("open"), card("done", TaskStatus.DONE, doneAt = now)))

            assertEquals(TransitionOutcome.CHANGED, repository.remove(TaskCardId("open")))
            assertEquals(TransitionOutcome.WRONG_STATUS, repository.remove(TaskCardId("done")))
            assertEquals(TransitionOutcome.NOT_FOUND, repository.remove(TaskCardId("open")))
        }

    @Test
    fun `migration V8 enforces status and points checks`() =
        runTest {
            suspend fun insert(
                status: String,
                points: Int,
            ) = dbQuery(db) {
                exec(
                    "INSERT INTO task_cards (id, member_id, title, points, status, assigned_at) " +
                        "VALUES ('x', '${childId.value}', 't', $points, '$status', 0)",
                )
            }

            assertFailsWith<Exception> { insert("X", 1) }
            assertFailsWith<Exception> { insert("TODO", 0) }
            assertFailsWith<Exception> { insert("TODO", 101) }
        }

    @Test
    fun `deleting a catalog task keeps its cards`() =
        runTest {
            val catalog = ExposedCatalogRepository(db)
            catalog.create(
                de.familyplanner.tasks.catalog
                    .CatalogTask(
                        de.familyplanner.tasks.catalog
                            .CatalogTaskId("cat"),
                        "T",
                        null,
                        1,
                        false,
                    ),
                now,
            )
            repository.createAll(
                listOf(
                    card("a").copy(
                        catalogTaskId =
                            de.familyplanner.tasks.catalog
                                .CatalogTaskId("cat"),
                    ),
                ),
            )

            dbQuery(db) { exec("DELETE FROM task_catalog WHERE id = 'cat'") }

            assertNull(repository.findById(TaskCardId("a"))?.catalogTaskId)
        }
}
