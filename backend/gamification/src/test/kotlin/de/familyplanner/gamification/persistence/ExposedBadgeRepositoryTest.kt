package de.familyplanner.gamification.persistence

import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.ExposedMemberRepository
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.core.persistence.DatabaseFactory
import de.familyplanner.core.persistence.MembersTable
import de.familyplanner.core.persistence.dbQuery
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExposedBadgeRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private lateinit var db: Database
    private lateinit var repository: ExposedBadgeRepository
    private var childId = MemberId("unset")

    @BeforeTest
    fun setUp() =
        runTest {
            db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))
            childId = ExposedMemberRepository(db).create(aMember(role = Role.CHILD)).id
            repository = ExposedBadgeRepository(db)
        }

    @Test
    fun `REQ-GAME-020 award is idempotent`() =
        runTest {
            assertTrue(repository.award(childId, "first-task", now))
            assertFalse(repository.award(childId, "first-task", now.plusSeconds(60)))

            assertEquals(mapOf("first-task" to now), repository.awardedOf(childId))
        }

    @Test
    fun `REQ-GAME-020 awards are removed with the member`() =
        runTest {
            repository.award(childId, "first-task", now)

            dbQuery(db) { MembersTable.deleteWhere { MembersTable.id eq childId.value } }

            assertTrue(repository.awardedOf(childId).isEmpty())
        }
}
