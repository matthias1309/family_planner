package de.familyplanner.gamification.persistence

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.gamification.ledger.EntryKind
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.LedgerEntryId
import de.familyplanner.gamification.ledger.Totals
import de.familyplanner.gamification.settings.GameSettings
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedLedgerRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private lateinit var db: Database
    private lateinit var repository: ExposedLedgerRepository
    private var childId = MemberId("unset")

    @BeforeTest
    fun setUp() =
        runTest {
            db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))
            childId = ExposedMemberRepository(db).create(aMember(role = Role.CHILD)).id
            repository = ExposedLedgerRepository(db)
        }

    private fun entry(
        id: String,
        kind: EntryKind = EntryKind.TASK,
        amount: Int = 2,
        key: String? = null,
        at: Instant = now,
    ) = LedgerEntry(LedgerEntryId(id), childId, kind, amount, "Titel", null, key, null, at)

    @Test
    fun `REQ-GAME-010 duplicate source key is ignored`() =
        runTest {
            assertTrue(repository.append(entry("a", key = "task:1")))
            assertEquals(false, repository.append(entry("b", key = "task:1")))

            assertEquals(1, repository.entriesOf(childId, 10).size)
        }

    @Test
    fun `REQ-GAME-013 manual entries without source key are all kept`() =
        runTest {
            repository.append(entry("a", EntryKind.MANUAL))
            repository.append(entry("b", EntryKind.MANUAL))

            assertEquals(2, repository.entriesOf(childId, 10).size)
        }

    @Test
    fun `REQ-GAME-012 totals and entries newest first with limit`() =
        runTest {
            repository.append(entry("a", amount = 2))
            repository.append(entry("b", EntryKind.COOKING, 3))
            repository.append(entry("c", EntryKind.SHOP, -4))
            repository.append(entry("old", amount = 1, at = now.minusSeconds(60)))

            assertEquals(Totals(collected = 6, balance = 2), repository.totalsOf(childId))
            assertEquals(mapOf(childId to Totals(6, 2)), repository.allTotals())
            assertEquals(listOf("c", "b"), repository.entriesOf(childId, 2).map { it.id.value })
            assertEquals(
                "old",
                repository
                    .entriesOf(childId, 10)
                    .last()
                    .id.value,
            )
            assertEquals(Totals.ZERO, repository.totalsOf(MemberId("nobody")))
        }

    @Test
    fun `REQ-GAME-020 allEntriesOf returns every entry of the member only`() =
        runTest {
            val other = ExposedMemberRepository(db).create(aMember(role = Role.CHILD)).id
            repeat(120) { repository.append(entry("a$it")) }
            repository.append(entry("other").copy(memberId = other))

            val all = repository.allEntriesOf(childId)

            assertEquals(120, all.size)
            assertTrue(all.all { it.memberId == childId })
            assertEquals("a0", all.first().id.value)
        }

    @Test
    fun `migration V10 enforces kind and non-zero amount`() =
        runTest {
            suspend fun insertRaw(
                kindValue: String,
                amountValue: Int,
            ) = dbQuery(db) {
                LedgerTable.insert {
                    it[id] = "x-$kindValue-$amountValue"
                    it[memberId] = childId.value
                    it[kind] = kindValue
                    it[amount] = amountValue
                    it[title] = "t"
                    it[bookedAt] = 1L
                }
            }

            assertFailsWith<Exception> { insertRaw("X", 1) }
            assertFailsWith<Exception> { insertRaw("TASK", 0) }
            assertTrue(dbQuery(db) { LedgerTable.selectAll().empty() })
        }

    @Test
    fun `REQ-GAME-011 settings repository stores a single row`() =
        runTest {
            val settings = ExposedGameSettingsRepository(db)
            assertNull(settings.find())

            settings.save(GameSettings(5), now)
            settings.save(GameSettings(7), now)

            assertEquals(GameSettings(7), settings.find())
        }
}
