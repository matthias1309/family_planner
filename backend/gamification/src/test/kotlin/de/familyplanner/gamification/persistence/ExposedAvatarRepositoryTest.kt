package de.familyplanner.gamification.persistence

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.core.persistence.MembersTable
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.gamification.avatar.AvatarCatalog
import de.familyplanner.gamification.avatar.PurchaseOutcome
import de.familyplanner.gamification.ledger.EntryKind
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.LedgerEntryId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedAvatarRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-11-01T10:00:00Z")
    private val item = checkNotNull(AvatarCatalog.find("eyes.hearts"))
    private lateinit var db: Database
    private lateinit var ledger: ExposedLedgerRepository
    private lateinit var repository: ExposedAvatarRepository
    private var parentId = MemberId("unset")
    private var childId = MemberId("unset")

    @BeforeTest
    fun setUp() =
        runTest {
            db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))
            val members = ExposedMemberRepository(db)
            parentId = members.create(aMember(role = Role.PARENT)).id
            childId = members.create(aMember(role = Role.CHILD)).id
            ledger = ExposedLedgerRepository(db)
            repository = ExposedAvatarRepository(db)
        }

    private suspend fun credit(amount: Int) {
        ledger.append(LedgerEntry(LedgerEntryId("credit-$amount"), childId, EntryKind.MANUAL, amount, "Start", null, null, parentId, now))
    }

    @Test
    fun `REQ-GAME-031 purchase books ownership and SHOP debit with source key together`() =
        runTest {
            credit(25)

            val outcome = repository.purchase(childId, item, "debit-1", now)

            assertEquals(PurchaseOutcome.Purchased(15), outcome)
            assertEquals(setOf(item.id), repository.ownedOf(childId))
            val debit = ledger.allEntriesOf(childId).last()
            assertEquals(EntryKind.SHOP, debit.kind)
            assertEquals(-10, debit.amount)
            assertEquals("shop:${childId.value}:${item.id}", debit.sourceKey)
            assertEquals("Shop: Herzaugen", debit.title)
            assertEquals(25, ledger.totalsOf(childId).collected)
            assertEquals(15, ledger.totalsOf(childId).balance)
        }

    @Test
    fun `REQ-GAME-031 second purchase is AlreadyOwned and books once`() =
        runTest {
            credit(50)
            repository.purchase(childId, item, "debit-1", now)

            assertEquals(PurchaseOutcome.AlreadyOwned, repository.purchase(childId, item, "debit-2", now))

            assertEquals(2, ledger.allEntriesOf(childId).size)
            assertEquals(40, ledger.totalsOf(childId).balance)
        }

    @Test
    fun `REQ-GAME-031 insufficient balance stores nothing`() =
        runTest {
            credit(9)

            assertEquals(PurchaseOutcome.InsufficientBalance(9, 10), repository.purchase(childId, item, "debit-1", now))

            assertTrue(repository.ownedOf(childId).isEmpty())
            assertEquals(1, ledger.allEntriesOf(childId).size)
        }

    @Test
    fun `REQ-GAME-031 failing ledger write rolls the ownership back`() =
        runTest {
            credit(50)
            val clash =
                LedgerEntry(
                    LedgerEntryId("clash"),
                    childId,
                    EntryKind.MANUAL,
                    1,
                    "Clash",
                    null,
                    "shop:${childId.value}:${item.id}",
                    parentId,
                    now,
                )
            assertTrue(ledger.append(clash))

            assertFails { repository.purchase(childId, item, "debit-1", now) }

            assertTrue(repository.ownedOf(childId).isEmpty())
        }

    @Test
    fun `REQ-GAME-030 save replaces all slots and find returns them with the update time`() =
        runTest {
            repository.save(childId, mapOf("skin" to "skin.edb98a", "eyes" to "eyes.default", "accessories" to "accessories.round"), now)
            val later = now.plusSeconds(60)

            repository.save(childId, mapOf("skin" to "skin.ae5d29", "eyes" to "eyes.happy"), later)

            val stored = checkNotNull(repository.find(childId))
            assertEquals(mapOf("skin" to "skin.ae5d29", "eyes" to "eyes.happy"), stored.items)
            assertEquals(later, stored.updatedAt)
        }

    @Test
    fun `REQ-GAME-030 findAll returns every member with an avatar and none without`() =
        runTest {
            val other = ExposedMemberRepository(db).create(aMember(role = Role.CHILD)).id
            repository.save(childId, mapOf("skin" to "skin.edb98a"), now)

            val all = repository.findAll()

            assertEquals(setOf(childId), all.keys)
            assertNull(repository.find(other))
        }

    @Test
    fun `REQ-GAME-030 deleting the member removes avatar and ownership`() =
        runTest {
            credit(50)
            repository.purchase(childId, item, "debit-1", now)
            repository.save(childId, mapOf("skin" to "skin.edb98a"), now)

            dbQuery(db) { MembersTable.deleteWhere { MembersTable.id eq childId.value } }

            assertTrue(repository.findAll().isEmpty())
            assertTrue(repository.ownedOf(childId).isEmpty())
        }

    @Test
    fun `REQ-GAME-031 price must be positive in the database`() =
        runTest {
            assertFails {
                dbQuery(db) {
                    AvatarItemTable.insert {
                        it[memberId] = childId.value
                        it[itemId] = "eyes.free"
                        it[price] = 0
                        it[purchasedAt] = now.toEpochMilli()
                    }
                }
            }

            assertTrue(dbQuery(db) { AvatarItemTable.selectAll().count() } == 0L)
        }
}
