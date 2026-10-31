package de.familyplanner.gamification.persistence

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.gamification.rewards.Reward
import de.familyplanner.gamification.rewards.RewardId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedRewardRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private lateinit var db: Database
    private lateinit var repository: ExposedRewardRepository
    private var parentId = MemberId("unset")
    private var childId = MemberId("unset")

    @BeforeTest
    fun setUp() =
        runTest {
            db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))
            val members = ExposedMemberRepository(db)
            parentId = members.create(aMember(role = Role.PARENT)).id
            childId = members.create(aMember(role = Role.CHILD)).id
            repository = ExposedRewardRepository(db)
        }

    private fun reward(
        id: String,
        title: String = "Eis",
        price: Int = 10,
    ) = Reward(RewardId(id), title, null, price, archived = false)

    @Test
    fun `REQ-GAME-040 create, update and archive`() =
        runTest {
            repository.create(reward("r1"), parentId, now)

            assertTrue(repository.update(reward("r1", "Kino", 80), now))
            assertEquals(reward("r1", "Kino", 80), repository.findById(RewardId("r1")))
            assertTrue(repository.archive(RewardId("r1"), now))
            assertTrue(repository.archive(RewardId("r1"), now))
            assertFalse(repository.archive(RewardId("nope"), now))
            assertFalse(repository.update(reward("r1"), now))
            assertFalse(repository.update(reward("nope"), now))
            assertEquals(true, repository.findById(RewardId("r1"))?.archived)
        }

    @Test
    fun `REQ-GAME-040 sorted by price then title and archived hidden by default`() =
        runTest {
            repository.create(reward("a", "zebra", 5), parentId, now)
            repository.create(reward("b", "Apfel", 20), parentId, now)
            repository.create(reward("c", "birne", 20), parentId, now)
            repository.create(reward("d", "alt", 1), parentId, now)
            repository.archive(RewardId("d"), now)

            assertEquals(listOf("a", "b", "c"), repository.findAll(false).map { it.id.value })
            assertEquals(listOf("d", "a", "b", "c"), repository.findAll(true).map { it.id.value })
        }

    @Test
    fun `REQ-GAME-040 price check constraint rejects out of range prices`() =
        runTest {
            assertFailsWith<Exception> {
                dbQuery(db) {
                    RewardTable.insert {
                        it[id] = "bad"
                        it[title] = "x"
                        it[price] = 10_001
                        it[createdAt] = 1
                        it[updatedAt] = 1
                    }
                }
            }
        }

    @Test
    fun `REQ-GAME-041 goal upsert and clear`() =
        runTest {
            repository.create(reward("r1"), parentId, now)
            repository.create(reward("r2"), parentId, now)

            assertNull(repository.goalOf(childId))
            repository.setGoal(childId, RewardId("r1"), now)
            repository.setGoal(childId, RewardId("r2"), now)
            assertEquals(RewardId("r2"), repository.goalOf(childId))
            repository.clearGoal(childId)
            repository.clearGoal(childId)
            assertNull(repository.goalOf(childId))
        }
}
