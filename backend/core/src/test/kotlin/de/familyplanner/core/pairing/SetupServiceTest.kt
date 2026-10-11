package de.familyplanner.core.pairing

import de.familyplanner.core.error.AppException
import de.familyplanner.core.fakes.FakeSecretGenerator
import de.familyplanner.core.fakes.InMemoryMemberRepository
import de.familyplanner.core.fakes.InMemoryStore
import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.member.Role
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SetupServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-12T10:00:00Z"))
    private val store = InMemoryStore()
    private val secrets = FakeSecretGenerator()

    private fun service(configuredToken: String? = SETUP_TOKEN) =
        SetupService(configuredToken, InMemoryMemberRepository(store), secrets, clock)

    @Test
    fun `REQ-AUTH-002 setup creates first parent and device`() =
        runTest {
            // Act
            val result = service().setup(SETUP_TOKEN, "Mama", "Handy Mama")

            // Assert
            assertEquals(Role.PARENT, result.member.role)
            assertEquals("Mama", result.member.name)
            assertEquals(result.member.id, result.device.memberId)
            assertEquals("Handy Mama", result.device.name)
            assertEquals(1, store.members.size)
            assertEquals(1, store.devices.size)
        }

    @Test
    fun `REQ-AUTH-002 setup with wrong token is rejected`() =
        runTest {
            assertFailsWith<AppException.Unauthorized> { service().setup("falsches-token-123456", "Mama", "Handy") }
        }

    @Test
    fun `REQ-AUTH-002 setup is rejected once members exist`() =
        runTest {
            // Arrange
            service().setup(SETUP_TOKEN, "Mama", "Handy Mama")

            // Act + Assert
            assertFailsWith<AppException.Conflict> { service().setup(SETUP_TOKEN, "Papa", "Handy Papa") }
        }

    @Test
    fun `REQ-AUTH-002 setup is disabled without configured token`() =
        runTest {
            assertFailsWith<AppException.SetupDisabled> { service(configuredToken = null).setup(SETUP_TOKEN, "Mama", "Handy") }
            assertFailsWith<AppException.SetupDisabled> { service(configuredToken = "").setup("", "Mama", "Handy") }
        }

    @Test
    fun `REQ-AUTH-002 setup validates names`() =
        runTest {
            assertFailsWith<AppException.Validation> { service().setup(SETUP_TOKEN, "", "Handy") }
            assertFailsWith<AppException.Validation> { service().setup(SETUP_TOKEN, "Mama", "x".repeat(61)) }
        }

    @Test
    fun `token and code are never stored in plain text`() =
        runTest {
            // Act
            val result = service().setup(SETUP_TOKEN, "Mama", "Handy")

            // Assert
            assertEquals(TokenHasherAccess.hash(result.token), store.devices.single().tokenHash)
        }

    private companion object {
        const val SETUP_TOKEN = "test-setup-token-123456"
    }
}
