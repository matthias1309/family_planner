package de.familyplanner.gamification.persistence

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.core.persistence.MembersTable
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.gamification.ledger.EntryKind
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.LedgerEntryId
import de.familyplanner.gamification.rewards.DecisionOutcome
import de.familyplanner.gamification.rewards.RedeemOutcome
import de.familyplanner.gamification.rewards.RedemptionId
import de.familyplanner.gamification.rewards.RedemptionStatus
import de.familyplanner.gamification.rewards.Reward
import de.familyplanner.gamification.rewards.RewardId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedRedemptionRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private lateinit var db: Database
    private lateinit var rewards: ExposedRewardRepository
    private lateinit var ledger: ExposedLedgerRepository
    private lateinit var repository: ExposedRedemptionRepository
    private var parentId = MemberId("unset")
    private var childId = MemberId("unset")
    private val rewardId = RewardId("reward-1")

    @BeforeTest
    fun setUp() =
        runTest {
            db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))
            val members = ExposedMemberRepository(db)
            parentId = members.create(aMember(role = Role.PARENT)).id
            childId = members.create(aMember(role = Role.CHILD)).id
            rewards = ExposedRewardRepository(db)
            ledger = ExposedLedgerRepository(db)
            repository = ExposedRedemptionRepository(db)
            rewards.create(Reward(rewardId, "Freizeitpark", "🎢", 200, archived = false), parentId, now)
        }

    private suspend fun credit(amount: Int) {
        ledger.append(LedgerEntry(LedgerEntryId("credit-$amount"), childId, EntryKind.MANUAL, amount, "Start", null, null, parentId, now))
    }

    private suspend fun redeem(
        id: String,
        at: Instant = now,
    ) = repository.redeem(RedemptionId(id), childId, rewardId, "debit-$id", at)

    @Test
    fun `REQ-GAME-042 redeem books debit with source key in the same transaction`() =
        runTest {
            credit(250)

            val outcome = redeem("red-1")

            val redeemed = assertIs<RedeemOutcome.Redeemed>(outcome)
            assertEquals(RedemptionStatus.PENDING, redeemed.redemption.status)
            assertEquals(200, redeemed.redemption.price)
            val debit = ledger.allEntriesOf(childId).last()
            assertEquals(EntryKind.REWARD, debit.kind)
            assertEquals(-200, debit.amount)
            assertEquals("reward:red-1", debit.sourceKey)
            assertEquals("Belohnung: Freizeitpark", debit.title)
            assertEquals(50, ledger.totalsOf(childId).balance)
            assertEquals(250, ledger.totalsOf(childId).collected)
        }

    @Test
    fun `REQ-GAME-042 insufficient balance stores nothing`() =
        runTest {
            credit(199)

            assertEquals(RedeemOutcome.InsufficientBalance(199, 200), redeem("red-1"))
            assertEquals(RedeemOutcome.RewardNotFound, repository.redeem(RedemptionId("x"), childId, RewardId("nope"), "d", now))

            assertTrue(repository.find(null, null, 10).isEmpty())
            assertEquals(1, ledger.allEntriesOf(childId).size)
        }

    @Test
    fun `REQ-GAME-042 archived reward cannot be redeemed`() =
        runTest {
            credit(500)
            rewards.archive(rewardId, now)

            assertEquals(RedeemOutcome.RewardArchived, redeem("red-1"))
        }

    @Test
    fun `REQ-GAME-042 two redeems for exactly one price succeed once`() =
        runTest {
            credit(200)

            assertIs<RedeemOutcome.Redeemed>(redeem("red-1"))
            assertEquals(RedeemOutcome.InsufficientBalance(0, 200), redeem("red-2"))

            assertEquals(1, repository.find(null, null, 10).size)
        }

    @Test
    fun `REQ-GAME-042 redeeming the goal reward clears the goal`() =
        runTest {
            credit(200)
            rewards.setGoal(childId, rewardId, now)

            redeem("red-1")

            assertNull(rewards.goalOf(childId))
        }

    @Test
    fun `REQ-GAME-043 reject books refund once`() =
        runTest {
            credit(200)
            redeem("red-1")

            val first = repository.reject(RedemptionId("red-1"), parentId, "Erst im Sommer", now, "refund-1")
            val second = repository.reject(RedemptionId("red-1"), parentId, "Nochmal", now, "refund-2")

            assertEquals(DecisionOutcome.CHANGED, first)
            assertEquals(DecisionOutcome.NOT_PENDING, second)
            assertEquals(200, ledger.totalsOf(childId).balance)
            val refund = ledger.allEntriesOf(childId).last()
            assertEquals(EntryKind.REWARD_REFUND, refund.kind)
            assertEquals("reward-refund:red-1", refund.sourceKey)
            assertEquals("Erst im Sommer", refund.note)
            val stored = checkNotNull(repository.findById(RedemptionId("red-1")))
            assertEquals(RedemptionStatus.REJECTED, stored.status)
            assertEquals("Erst im Sommer", stored.rejectionReason)
        }

    @Test
    fun `REQ-GAME-043 fulfill after reject is NOT_PENDING and unknown is NOT_FOUND`() =
        runTest {
            credit(200)
            redeem("red-1")
            repository.reject(RedemptionId("red-1"), parentId, "Nein", now, "refund-1")

            assertEquals(DecisionOutcome.NOT_PENDING, repository.fulfill(RedemptionId("red-1"), parentId, now))
            assertEquals(DecisionOutcome.NOT_FOUND, repository.fulfill(RedemptionId("nope"), parentId, now))
            assertEquals(DecisionOutcome.NOT_FOUND, repository.reject(RedemptionId("nope"), parentId, "x", now, "refund-9"))
        }

    @Test
    fun `REQ-GAME-043 fulfill marks decided and keeps the debit`() =
        runTest {
            credit(200)
            redeem("red-1")

            assertEquals(DecisionOutcome.CHANGED, repository.fulfill(RedemptionId("red-1"), parentId, now.plusSeconds(5)))

            val stored = checkNotNull(repository.findById(RedemptionId("red-1")))
            assertEquals(RedemptionStatus.FULFILLED, stored.status)
            assertEquals(now.plusSeconds(5), stored.decidedAt)
            assertEquals(0, ledger.totalsOf(childId).balance)
        }

    @Test
    fun `REQ-GAME-042 find orders newest first and filters by member, status and limit`() =
        runTest {
            credit(600)
            val other = ExposedMemberRepository(db).create(aMember(role = Role.CHILD)).id
            ledger.append(LedgerEntry(LedgerEntryId("credit-o"), other, EntryKind.MANUAL, 300, "Start", null, null, parentId, now))
            redeem("red-1", now)
            repository.redeem(RedemptionId("red-2"), other, rewardId, "debit-red-2", now.plusSeconds(1))
            redeem("red-3", now.plusSeconds(1))
            repository.fulfill(RedemptionId("red-1"), parentId, now)

            assertEquals(listOf("red-3", "red-2", "red-1"), repository.find(null, null, 10).map { it.id.value })
            assertEquals(listOf("red-3", "red-1"), repository.find(childId, null, 10).map { it.id.value })
            assertEquals(listOf("red-3", "red-2"), repository.find(null, RedemptionStatus.PENDING, 10).map { it.id.value })
            assertEquals(listOf("red-3"), repository.find(null, null, 1).map { it.id.value })
        }

    @Test
    fun `REQ-GAME-042 redemptions and goal are removed with the member`() =
        runTest {
            credit(200)
            redeem("red-1")
            rewards.setGoal(childId, rewardId, now)

            dbQuery(db) { MembersTable.deleteWhere { MembersTable.id eq childId.value } }

            assertTrue(repository.find(null, null, 10).isEmpty())
            assertNull(rewards.goalOf(childId))
        }
}
