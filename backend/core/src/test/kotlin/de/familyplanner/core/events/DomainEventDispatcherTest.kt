package de.familyplanner.core.events

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.core.persistence.DomainEventsTable
import de.familyplanner.core.persistence.dbQuery
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainEventDispatcherTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private lateinit var db: Database
    private lateinit var dispatcher: DomainEventDispatcher

    @BeforeTest
    fun setUp() {
        db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))
        dispatcher = DomainEventDispatcher(db)
    }

    private fun accepted(n: Int) = TaskAccepted("card-$n", "m", "t$n", n, "p", now.toEpochMilli())

    private suspend fun record(vararg numbers: Int) = dbQuery(db) { numbers.forEach { recordEvent(accepted(it), now) } }

    private fun collect(into: MutableList<Long>): suspend (StoredEvent) -> Unit = { into += it.seq }

    @Test
    fun `recorded events reach a subscriber in order`() =
        runTest {
            val seen = mutableListOf<Long>()
            dispatcher.subscribe("a", collect(seen))
            record(1, 2, 3)

            dispatcher.dispatchPending()

            assertEquals(listOf(1L, 2L, 3L), seen)
        }

    @Test
    fun `a new subscriber catches up from the beginning`() =
        runTest {
            record(1, 2)
            val seen = mutableListOf<Long>()

            dispatcher.subscribe("late", collect(seen))
            dispatcher.dispatchPending()

            assertEquals(listOf(1L, 2L), seen)
        }

    @Test
    fun `a failing subscriber retries from the failed event and does not block others`() =
        runTest {
            val a = mutableListOf<Long>()
            val b = mutableListOf<Long>()
            var failing = true
            dispatcher.subscribe("a") {
                if (it.seq == 2L && failing) error("boom")
                a += it.seq
            }
            dispatcher.subscribe("b", collect(b))
            record(1, 2, 3)

            dispatcher.dispatchPending()
            assertEquals(listOf(1L), a)
            assertEquals(listOf(1L, 2L, 3L), b)

            failing = false
            dispatcher.dispatchPending()
            assertEquals(listOf(1L, 2L, 3L), a)
        }

    @Test
    fun `nothing is delivered twice after success`() =
        runTest {
            val seen = mutableListOf<Long>()
            dispatcher.subscribe("a", collect(seen))
            record(1, 2)

            dispatcher.dispatchPending()
            dispatcher.dispatchPending()

            assertEquals(listOf(1L, 2L), seen)
        }

    @Test
    fun `more events than one batch are all delivered`() =
        runTest {
            val seen = mutableListOf<Long>()
            dispatcher.subscribe("a", collect(seen))
            record(*IntArray(150) { it })

            dispatcher.dispatchPending()

            assertEquals(150, seen.size)
        }

    @Test
    fun `event of a rolled back transaction is not delivered`() =
        runTest {
            runCatching {
                dbQuery(db) {
                    recordEvent(accepted(1), now)
                    error("rollback")
                }
            }

            assertEquals(0, dbQuery(db) { DomainEventsTable.selectAll().count() }.toInt())
        }

    @Test
    fun `unknown event types arrive with a null event`() =
        runTest {
            dbQuery(db) {
                DomainEventsTable.insert {
                    it[type] = "future.v9"
                    it[payload] = "{}"
                    it[occurredAt] = now.toEpochMilli()
                }
            }
            val events = mutableListOf<StoredEvent>()
            dispatcher.subscribe("a") { events += it }

            dispatcher.dispatchPending()

            assertEquals(listOf(null), events.map { it.event })
        }

    @Test
    fun `dispatching without subscribers is a no-op`() = runTest { dispatcher.dispatchPending() }
}
