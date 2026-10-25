package de.familyplanner.gamification.persistence

import de.familyplanner.core.events.DomainEventDispatcher
import de.familyplanner.core.events.TaskAccepted
import de.familyplanner.core.events.recordEvent
import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.core.persistence.EventCursorsTable
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.gamification.POINTS_SUBSCRIBER
import de.familyplanner.gamification.ledger.PointsEventHandler
import de.familyplanner.gamification.settings.GameSettingsService
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PointsSubscriptionTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private lateinit var db: Database
    private lateinit var dispatcher: DomainEventDispatcher
    private lateinit var ledger: ExposedLedgerRepository
    private var childId = MemberId("unset")

    @BeforeTest
    fun setUp() =
        runTest {
            val clock = MutableClock(now)
            db = DatabaseFactory.create(dir.resolve("t.db"), clock)
            val members = ExposedMemberRepository(db)
            childId = members.create(aMember(role = Role.CHILD)).id
            ledger = ExposedLedgerRepository(db)
            val handler = PointsEventHandler(members, ledger, GameSettingsService(ExposedGameSettingsRepository(db), clock))
            dispatcher = DomainEventDispatcher(db)
            dispatcher.subscribe(POINTS_SUBSCRIBER, handler::handle)
        }

    private suspend fun recordAccepted(vararg cards: String) =
        dbQuery(db) {
            cards.forEach { recordEvent(TaskAccepted(it, childId.value, "Bad", 2, childId.value, now.toEpochMilli()), now) }
        }

    @Test
    fun `REQ-GAME-010 events recorded before subscribing are credited once`() =
        runTest {
            recordAccepted("c1", "c2")

            dispatcher.dispatchPending()
            dispatcher.dispatchPending()

            assertEquals(2, ledger.entriesOf(childId, 10).size)
            assertEquals(4, ledger.totalsOf(childId).balance)
        }

    @Test
    fun `REQ-GAME-010 redelivery after a lost cursor does not double credit`() =
        runTest {
            recordAccepted("c1", "c2")
            dispatcher.dispatchPending()

            dbQuery(db) { EventCursorsTable.deleteAll() }
            dispatcher.dispatchPending()

            assertEquals(2, ledger.entriesOf(childId, 10).size)
        }
}
