package de.familyplanner.android.feature.auth.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.auth.fakes.FakeAuthRepository
import de.familyplanner.android.feature.auth.fakes.FakeFamilyRepository
import de.familyplanner.android.feature.auth.fakes.aSession
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UseCasesTest {
    private val auth = FakeAuthRepository()
    private val family = FakeFamilyRepository()
    private val sessions = InMemorySessionStore()

    @Test
    fun `REQ-AUTH-002 pairing saves session on success`() =
        runTest {
            val result = PairDeviceUseCase(auth, sessions)("123456", "Pixel")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(aSession, sessions.current)
        }

    @Test
    fun `REQ-AUTH-002 invalid pairing code does not save session`() =
        runTest {
            auth.pairResult = ApiResult.Failure(AppError.InvalidPairingCode)

            val result = PairDeviceUseCase(auth, sessions)("123456", "Pixel")

            assertEquals(ApiResult.Failure(AppError.InvalidPairingCode), result)
            assertNull(sessions.current)
        }

    @Test
    fun `REQ-AUTH-002 malformed code is rejected locally without request`() =
        runTest {
            val result = PairDeviceUseCase(auth, sessions)("12a456", "Pixel")

            assertIs<ApiResult.Failure>(result)
            assertEquals(0, auth.calls)
        }

    @Test
    fun `REQ-AUTH-002 setup with existing family reports conflict`() =
        runTest {
            auth.setupResult = ApiResult.Failure(AppError.Conflict("Bereits eingerichtet"))

            val result = SetupFamilyUseCase(auth, sessions)("0123456789abcdef", "Mama", "Pixel")

            assertEquals(ApiResult.Failure(AppError.Conflict("Bereits eingerichtet")), result)
            assertNull(sessions.current)
        }

    @Test
    fun `REQ-AUTH-002 setup token shorter than 16 is rejected locally`() =
        runTest {
            val result = SetupFamilyUseCase(auth, sessions)("012345678901234", "Mama", "Pixel")

            assertIs<ApiResult.Failure>(result)
            assertEquals(0, auth.calls)
        }

    @Test
    fun `setup saves session and trims inputs`() =
        runTest {
            val result = SetupFamilyUseCase(auth, sessions)("  0123456789abcdef  ", " Mama ", " Pixel ")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(aSession, sessions.current)
        }

    @Test
    fun `REQ-AUTH-001 create member rejects blank or too long name without request`() =
        runTest {
            val useCase = CreateMemberUseCase(family)

            assertIs<ApiResult.Failure>(useCase("  ", Role.CHILD))
            assertIs<ApiResult.Failure>(useCase("x".repeat(AuthLimits.NAME_MAX + 1), Role.CHILD))
            assertEquals(emptyList(), family.createdMembers)
        }

    @Test
    fun `REQ-AUTH-001 create member trims name and forwards role`() =
        runTest {
            CreateMemberUseCase(family)("  Max ", Role.PARENT)

            assertEquals(listOf("Max" to Role.PARENT), family.createdMembers)
        }

    @Test
    fun `REQ-AUTH-001 duplicate member name surfaces conflict`() =
        runTest {
            family.createMemberResult = ApiResult.Failure(AppError.Conflict("Name existiert bereits"))

            val result = CreateMemberUseCase(family)("Lea", Role.CHILD)

            assertEquals(ApiResult.Failure(AppError.Conflict("Name existiert bereits")), result)
        }

    @Test
    fun `REQ-AUTH-003 revoke device calls delete endpoint`() =
        runTest {
            sessions.setCurrent(aSession)

            RevokeDeviceUseCase(family, sessions)("d-9")

            assertEquals(listOf("d-9"), family.revokedIds)
            assertEquals(aSession, sessions.current)
        }

    @Test
    fun `REQ-AUTH-003 revoking own device signs out`() =
        runTest {
            sessions.setCurrent(aSession)

            RevokeDeviceUseCase(family, sessions)(aSession.deviceId)

            assertNull(sessions.current)
        }

    @Test
    fun `failed revoke of own device keeps session`() =
        runTest {
            sessions.setCurrent(aSession)
            family.revokeResult = ApiResult.Failure(AppError.Network)

            RevokeDeviceUseCase(family, sessions)(aSession.deviceId)

            assertEquals(aSession, sessions.current)
        }

    @Test
    fun `refresh me updates name and role in session`() =
        runTest {
            sessions.setCurrent(aSession)
            auth.meResult = ApiResult.Success(Me("m-1", "Lea-Marie", Role.PARENT, "d-1"))

            RefreshMeUseCase(auth, sessions)()

            assertEquals(aSession.copy(memberName = "Lea-Marie", role = Role.PARENT), sessions.current)
        }

    @Test
    fun `refresh me keeps session when offline`() =
        runTest {
            sessions.setCurrent(aSession)
            auth.meResult = ApiResult.Failure(AppError.Network)

            val result = RefreshMeUseCase(auth, sessions)()

            assertEquals(ApiResult.Failure(AppError.Network), result)
            assertEquals(aSession, sessions.current)
        }

    @Test
    fun `load family combines members and devices and first error wins`() =
        runTest {
            family.membersResult = ApiResult.Success(listOf(FamilyMember("m-1", "Lea", Role.CHILD)))
            family.devicesResult = ApiResult.Success(listOf(FamilyDevice("d-1", "m-1", "Pixel", Instant.EPOCH)))
            val ok = LoadFamilyUseCase(family)()
            assertIs<ApiResult.Success<FamilyOverview>>(ok)
            assertEquals(1, ok.data.members.size)
            assertEquals(1, ok.data.devices.size)

            family.membersResult = ApiResult.Failure(AppError.Forbidden)
            family.devicesResult = ApiResult.Failure(AppError.Network)
            assertEquals(ApiResult.Failure(AppError.Forbidden), LoadFamilyUseCase(family)())

            family.membersResult = ApiResult.Success(emptyList())
            assertEquals(ApiResult.Failure(AppError.Network), LoadFamilyUseCase(family)())
        }

    @Test
    fun `create pairing code delegates to repository`() =
        runTest {
            val result = CreatePairingCodeUseCase(family)("m-1")

            assertIs<ApiResult.Success<IssuedPairingCode>>(result)
            assertEquals("483920", result.data.code)
        }
}
