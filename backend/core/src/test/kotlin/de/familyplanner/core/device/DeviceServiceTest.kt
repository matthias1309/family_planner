package de.familyplanner.core.device

import de.familyplanner.core.error.AppException
import de.familyplanner.core.fakes.FakeSecretGenerator
import de.familyplanner.core.fakes.InMemoryDeviceRepository
import de.familyplanner.core.fakes.InMemoryMemberRepository
import de.familyplanner.core.fakes.InMemoryPairingCodeRepository
import de.familyplanner.core.fakes.InMemoryStore
import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.Role
import de.familyplanner.core.pairing.PairingResult
import de.familyplanner.core.pairing.PairingService
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeviceServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-12T10:00:00Z"))
    private val store = InMemoryStore()
    private val members = InMemoryMemberRepository(store)
    private val service = DeviceService(InMemoryDeviceRepository(store), clock)
    private val pairing = PairingService(members, InMemoryPairingCodeRepository(store), FakeSecretGenerator(), clock)

    private suspend fun pairedDevice(role: Role = Role.CHILD): PairingResult {
        val member = members.create(aMember(role = role))
        val code = pairing.createCode(member.id).code
        return pairing.pair(code, "Handy")
    }

    @Test
    fun `REQ-AUTH-002 token authenticates its device`() =
        runTest {
            // Arrange
            val paired = pairedDevice(Role.PARENT)

            // Act
            val principal = service.authenticate(paired.token)

            // Assert
            assertNotNull(principal)
            assertEquals(Role.PARENT, principal.role)
            assertEquals(paired.member.id, principal.memberId)
            assertEquals(paired.device.id, principal.deviceId)
        }

    @Test
    fun `REQ-AUTH-002 unknown token is rejected`() =
        runTest {
            assertNull(service.authenticate("unbekannt"))
        }

    @Test
    fun `REQ-AUTH-003 revoked device token is rejected`() =
        runTest {
            // Arrange
            val paired = pairedDevice()

            // Act
            service.revoke(paired.device.id)

            // Assert
            assertNull(service.authenticate(paired.token))
            assertEquals(emptyList(), service.list())
        }

    @Test
    fun `REQ-AUTH-003 revoking an unknown device fails`() =
        runTest {
            assertFailsWith<AppException.NotFound> { service.revoke(DeviceId("00000000-0000-0000-0000-000000000000")) }
        }

    @Test
    fun `last seen is updated at most every five minutes`() =
        runTest {
            // Arrange
            val paired = pairedDevice()

            // Act
            service.authenticate(paired.token)
            clock.advance(Duration.ofMinutes(1))
            service.authenticate(paired.token)
            clock.advance(Duration.ofMinutes(5))
            service.authenticate(paired.token)

            // Assert
            assertEquals(2, store.touchCount)
        }
}
