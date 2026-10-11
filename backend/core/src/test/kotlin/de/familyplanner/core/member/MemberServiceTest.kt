package de.familyplanner.core.member

import de.familyplanner.core.error.AppException
import de.familyplanner.core.fakes.InMemoryMemberRepository
import de.familyplanner.core.fakes.MutableClock
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemberServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-12T10:00:00Z"))
    private val service = MemberService(InMemoryMemberRepository(), clock)

    @Test
    fun `REQ-AUTH-001 parent creates member`() =
        runTest {
            // Act
            val member = service.create("Lea", Role.CHILD)

            // Assert
            assertEquals("Lea", member.name)
            assertEquals(Role.CHILD, member.role)
            assertEquals(clock.instant(), member.createdAt)
            assertEquals(listOf(member), service.list())
        }

    @Test
    fun `REQ-AUTH-001 name is trimmed`() =
        runTest {
            assertEquals("Lea", service.create("  Lea  ", Role.CHILD).name)
        }

    @Test
    fun `REQ-AUTH-001 blank name is rejected`() =
        runTest {
            assertFailsWith<AppException.Validation> { service.create("  ", Role.CHILD) }
        }

    @Test
    fun `REQ-AUTH-001 name longer than 40 chars is rejected`() =
        runTest {
            assertFailsWith<AppException.Validation> { service.create("x".repeat(41), Role.CHILD) }
        }

    @Test
    fun `REQ-AUTH-001 name of exactly 40 chars is accepted`() =
        runTest {
            assertEquals(40, service.create("x".repeat(40), Role.CHILD).name.length)
        }

    @Test
    fun `REQ-AUTH-001 duplicate name is rejected case-insensitively`() =
        runTest {
            // Arrange
            service.create("Lea", Role.CHILD)

            // Act + Assert
            assertFailsWith<AppException.Conflict> { service.create("lea", Role.PARENT) }
        }
}
