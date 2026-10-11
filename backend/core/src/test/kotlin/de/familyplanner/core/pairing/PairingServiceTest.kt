package de.familyplanner.core.pairing

import de.familyplanner.core.error.AppException
import de.familyplanner.core.fakes.FakeSecretGenerator
import de.familyplanner.core.fakes.InMemoryMemberRepository
import de.familyplanner.core.fakes.InMemoryPairingCodeRepository
import de.familyplanner.core.fakes.InMemoryStore
import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PairingServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-12T10:00:00Z"))
    private val store = InMemoryStore()
    private val members = InMemoryMemberRepository(store)
    private val secrets = FakeSecretGenerator(code = "123456", token = "tok")
    private val service = PairingService(members, InMemoryPairingCodeRepository(store), secrets, clock)

    @Test
    fun `REQ-AUTH-002 pairing code has six digits and expires in 15 minutes`() =
        runTest {
            // Arrange
            val child = members.create(aMember(role = Role.CHILD))

            // Act
            val issued = service.createCode(child.id)

            // Assert
            assertTrue(Regex("^\\d{6}$").matches(issued.code))
            assertEquals(clock.instant().plus(Duration.ofMinutes(15)), issued.expiresAt)
        }

    @Test
    fun `REQ-AUTH-002 valid code pairs device to member`() =
        runTest {
            // Arrange
            val child = members.create(aMember(role = Role.CHILD))
            val issued = service.createCode(child.id)

            // Act
            val result = service.pair(issued.code, "Handy Lea")

            // Assert
            assertEquals(child.id, result.device.memberId)
            assertEquals(child, result.member)
            assertEquals("Handy Lea", result.device.name)
            assertEquals(TokenHasherAccess.hash(result.token), store.devices.single().tokenHash)
        }

    @Test
    fun `REQ-AUTH-002 expired pairing code is rejected`() =
        runTest {
            // Arrange
            val child = members.create(aMember(role = Role.CHILD))
            service.createCode(child.id)
            clock.advance(Duration.ofMinutes(16))

            // Act + Assert
            assertFailsWith<AppException.InvalidPairingCode> { service.pair("123456", "Handy Lea") }
        }

    @Test
    fun `REQ-AUTH-002 pairing code can be used only once`() =
        runTest {
            // Arrange
            val child = members.create(aMember(role = Role.CHILD))
            service.createCode(child.id)
            service.pair("123456", "Handy Lea")

            // Act + Assert
            assertFailsWith<AppException.InvalidPairingCode> { service.pair("123456", "Zweites Handy") }
            assertEquals(1, store.devices.size)
        }

    @Test
    fun `REQ-AUTH-002 new code invalidates previous code`() =
        runTest {
            // Arrange
            val child = members.create(aMember(role = Role.CHILD))
            service.createCode(child.id)
            secrets.nextCode("654321")
            val second = service.createCode(child.id)

            // Act + Assert
            assertNotEquals("123456", second.code)
            assertFailsWith<AppException.InvalidPairingCode> { service.pair("123456", "Handy Lea") }
            assertEquals(child.id, service.pair("654321", "Handy Lea").device.memberId)
        }

    @Test
    fun `REQ-AUTH-002 unknown code is rejected`() =
        runTest {
            assertFailsWith<AppException.InvalidPairingCode> { service.pair("999999", "Handy") }
        }

    @Test
    fun `REQ-AUTH-002 malformed code is rejected`() =
        runTest {
            assertFailsWith<AppException.Validation> { service.pair("12ab", "Handy") }
        }

    @Test
    fun `REQ-AUTH-002 pairing validates device name`() =
        runTest {
            assertFailsWith<AppException.Validation> { service.pair("123456", "  ") }
        }

    @Test
    fun `REQ-AUTH-002 pairing code for unknown member fails`() =
        runTest {
            assertFailsWith<AppException.NotFound> { service.createCode(MemberId("00000000-0000-0000-0000-000000000000")) }
        }

    @Test
    fun `token and code are never stored in plain text`() =
        runTest {
            // Arrange
            val child = members.create(aMember(role = Role.CHILD))

            // Act
            service.createCode(child.id)

            // Assert
            assertEquals(TokenHasherAccess.hash("123456"), store.codes.single().codeHash)
        }
}
