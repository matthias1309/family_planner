package de.familyplanner.android.feature.auth.ui

import app.cash.turbine.test
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.auth.domain.CreateMemberUseCase
import de.familyplanner.android.feature.auth.domain.CreatePairingCodeUseCase
import de.familyplanner.android.feature.auth.domain.FamilyDevice
import de.familyplanner.android.feature.auth.domain.FamilyMember
import de.familyplanner.android.feature.auth.domain.LoadFamilyUseCase
import de.familyplanner.android.feature.auth.domain.Me
import de.familyplanner.android.feature.auth.domain.PairDeviceUseCase
import de.familyplanner.android.feature.auth.domain.RefreshMeUseCase
import de.familyplanner.android.feature.auth.domain.RevokeDeviceUseCase
import de.familyplanner.android.feature.auth.domain.SetupFamilyUseCase
import de.familyplanner.android.feature.auth.fakes.FakeAuthRepository
import de.familyplanner.android.feature.auth.fakes.FakeFamilyRepository
import de.familyplanner.android.feature.auth.fakes.aSession
import de.familyplanner.android.feature.auth.ui.family.FamilyEvent
import de.familyplanner.android.feature.auth.ui.family.FamilyViewModel
import de.familyplanner.android.feature.auth.ui.home.HomeViewModel
import de.familyplanner.android.feature.auth.ui.pair.PairEvent
import de.familyplanner.android.feature.auth.ui.pair.PairViewModel
import de.familyplanner.android.feature.auth.ui.setup.SetupEvent
import de.familyplanner.android.feature.auth.ui.setup.SetupViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val family = FakeFamilyRepository()
    private val sessions = InMemorySessionStore()

    private fun pairViewModel() = PairViewModel(PairDeviceUseCase(auth, sessions), "Pixel 8")

    private fun setupViewModel() = SetupViewModel(SetupFamilyUseCase(auth, sessions), "Pixel 8")

    private fun familyViewModel() =
        FamilyViewModel(
            sessions,
            LoadFamilyUseCase(family),
            CreateMemberUseCase(family),
            CreatePairingCodeUseCase(family),
            RevokeDeviceUseCase(family, sessions),
        )

    @Test
    fun `PairViewModel filters non digits and caps at six`() {
        val viewModel = pairViewModel()

        viewModel.onEvent(PairEvent.CodeChanged("12a3456789"))

        assertEquals("123456", viewModel.state.value.code)
    }

    @Test
    fun `PairViewModel submit enabled only with valid input`() {
        val viewModel = pairViewModel()
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onEvent(PairEvent.CodeChanged("12345"))
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onEvent(PairEvent.CodeChanged("123456"))
        assertTrue(viewModel.state.value.canSubmit)

        viewModel.onEvent(PairEvent.DeviceNameChanged("  "))
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `REQ-AUTH-002 pairing success stores session`() =
        runTest {
            val viewModel = pairViewModel()
            viewModel.onEvent(PairEvent.CodeChanged("123456"))

            viewModel.onEvent(PairEvent.Submit)

            assertEquals(aSession, sessions.current)
            assertEquals("", viewModel.state.value.code)
            assertFalse(viewModel.state.value.isSubmitting)
        }

    @Test
    fun `REQ-AUTH-002 invalid code shows error and keeps input`() =
        runTest {
            auth.pairResult = ApiResult.Failure(AppError.InvalidPairingCode)
            val viewModel = pairViewModel()
            viewModel.onEvent(PairEvent.CodeChanged("123456"))

            viewModel.state.test {
                skipItems(1)
                viewModel.onEvent(PairEvent.Submit)
                assertTrue(awaitItem().isSubmitting)
                val failed = awaitItem()
                assertEquals(AppError.InvalidPairingCode, failed.error)
                assertEquals("123456", failed.code)
            }
            assertNull(sessions.current)
        }

    @Test
    fun `submit ignored while already submitting or invalid`() =
        runTest {
            val viewModel = pairViewModel()

            viewModel.onEvent(PairEvent.Submit)

            assertEquals(0, auth.calls)
        }

    @Test
    fun `REQ-AUTH-002 setup conflict marks family as already set up`() =
        runTest {
            auth.setupResult = ApiResult.Failure(AppError.Conflict("x"))
            val viewModel = setupViewModel()
            viewModel.onEvent(SetupEvent.TokenChanged("0123456789abcdef"))
            viewModel.onEvent(SetupEvent.ParentNameChanged("Mama"))

            viewModel.onEvent(SetupEvent.Submit)

            assertTrue(viewModel.state.value.alreadySetUp)
            assertNull(sessions.current)
        }

    @Test
    fun `setup success stores session and drops the token`() =
        runTest {
            val viewModel = setupViewModel()
            viewModel.onEvent(SetupEvent.TokenChanged("0123456789abcdef"))
            viewModel.onEvent(SetupEvent.ParentNameChanged("Mama"))
            assertTrue(viewModel.state.value.canSubmit)

            viewModel.onEvent(SetupEvent.Submit)

            assertEquals(aSession, sessions.current)
            assertEquals("", viewModel.state.value.setupToken)
        }

    @Test
    fun `setup requires a token of at least sixteen characters`() {
        val viewModel = setupViewModel()
        viewModel.onEvent(SetupEvent.TokenChanged("012345678901234"))
        viewModel.onEvent(SetupEvent.ParentNameChanged("Mama"))

        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `HomeViewModel keeps session when offline`() =
        runTest {
            sessions.setCurrent(aSession)
            auth.meResult = ApiResult.Failure(AppError.Network)

            val viewModel = HomeViewModel(sessions, RefreshMeUseCase(auth, sessions))

            assertTrue(viewModel.state.value.isOffline)
            assertEquals(aSession, viewModel.state.value.session)
            assertEquals(aSession, sessions.current)
        }

    @Test
    fun `HomeViewModel is online after successful refresh and parent can manage family`() =
        runTest {
            sessions.setCurrent(aSession.copy(role = Role.PARENT))
            auth.meResult = ApiResult.Success(Me("m-1", "Lea", Role.PARENT, "d-1"))

            val viewModel = HomeViewModel(sessions, RefreshMeUseCase(auth, sessions))

            assertFalse(viewModel.state.value.isOffline)
            assertTrue(viewModel.state.value.canManageFamily)
        }

    @Test
    fun `FamilyViewModel loads members and devices`() =
        runTest {
            sessions.setCurrent(aSession)
            family.membersResult = ApiResult.Success(listOf(FamilyMember("m-1", "Lea", Role.CHILD)))
            family.devicesResult = ApiResult.Success(listOf(FamilyDevice("d-1", "m-1", "Pixel", Instant.EPOCH)))

            val state = familyViewModel().state.value

            assertEquals(1, state.members.size)
            assertEquals("Lea", state.memberName("m-1"))
            assertEquals("d-1", state.ownDeviceId)
            assertFalse(state.isLoading)
        }

    @Test
    fun `FamilyViewModel surfaces forbidden for children`() =
        runTest {
            family.membersResult = ApiResult.Failure(AppError.Forbidden)

            assertEquals(AppError.Forbidden, familyViewModel().state.value.error)
        }

    @Test
    fun `REQ-AUTH-001 duplicate member name keeps dialog open with conflict`() =
        runTest {
            family.createMemberResult = ApiResult.Failure(AppError.Conflict("Name existiert bereits"))
            val viewModel = familyViewModel()
            viewModel.onEvent(FamilyEvent.ShowAddMember)

            viewModel.onEvent(FamilyEvent.AddMember("Lea", Role.CHILD))

            assertTrue(viewModel.state.value.showAddMember)
            assertEquals(AppError.Conflict("Name existiert bereits"), viewModel.state.value.addMemberError)
        }

    @Test
    fun `REQ-AUTH-001 member creation closes dialog and reloads`() =
        runTest {
            val viewModel = familyViewModel()
            viewModel.onEvent(FamilyEvent.ShowAddMember)
            family.membersResult = ApiResult.Success(listOf(FamilyMember("m-2", "Max", Role.CHILD)))

            viewModel.onEvent(FamilyEvent.AddMember("Max", Role.CHILD))

            assertFalse(viewModel.state.value.showAddMember)
            assertEquals(1, viewModel.state.value.members.size)
        }

    @Test
    fun `REQ-AUTH-002 requesting a code shows it for that member`() =
        runTest {
            val viewModel = familyViewModel()

            viewModel.onEvent(FamilyEvent.RequestCode(FamilyMember("m-1", "Lea", Role.CHILD)))

            assertEquals(
                "Lea",
                viewModel.state.value.issuedCode
                    ?.memberName,
            )
            assertEquals(
                "483920",
                viewModel.state.value.issuedCode
                    ?.code
                    ?.code,
            )
            viewModel.onEvent(FamilyEvent.DismissCode)
            assertNull(viewModel.state.value.issuedCode)
        }

    @Test
    fun `REQ-AUTH-003 revoking requires confirmation and own device signs out`() =
        runTest {
            sessions.setCurrent(aSession)
            val own = FamilyDevice("d-1", "m-1", "Pixel", null)
            val viewModel = familyViewModel()

            viewModel.onEvent(FamilyEvent.RequestRevoke(own))
            assertTrue(viewModel.state.value.revokesOwnDevice)
            assertEquals(emptyList(), family.revokedIds)

            viewModel.onEvent(FamilyEvent.ConfirmRevoke)

            assertEquals(listOf("d-1"), family.revokedIds)
            assertNull(sessions.current)
            assertNull(viewModel.state.value.pendingRevoke)
        }

    @Test
    fun `cancelling revoke does not call the server`() =
        runTest {
            val viewModel = familyViewModel()
            viewModel.onEvent(FamilyEvent.RequestRevoke(FamilyDevice("d-2", "m-1", "Tab", null)))

            viewModel.onEvent(FamilyEvent.CancelRevoke)

            assertEquals(emptyList(), family.revokedIds)
            assertNull(viewModel.state.value.pendingRevoke)
        }

    @Test
    fun `failed revoke reports the error`() =
        runTest {
            family.revokeResult = ApiResult.Failure(AppError.Network)
            val viewModel = familyViewModel()
            viewModel.onEvent(FamilyEvent.RequestRevoke(FamilyDevice("d-2", "m-1", "Tab", null)))

            viewModel.onEvent(FamilyEvent.ConfirmRevoke)

            assertEquals(AppError.Network, viewModel.state.value.error)
        }
}
