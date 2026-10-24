package de.familyplanner.android.feature.auth.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.auth.domain.FamilyDevice
import de.familyplanner.android.feature.auth.domain.FamilyMember
import de.familyplanner.android.feature.auth.domain.IssuedPairingCode
import de.familyplanner.android.feature.auth.fakes.aSession
import de.familyplanner.android.feature.auth.ui.family.FamilyEvent
import de.familyplanner.android.feature.auth.ui.family.FamilyScreen
import de.familyplanner.android.feature.auth.ui.family.FamilyUiState
import de.familyplanner.android.feature.auth.ui.family.IssuedCodeDialog
import de.familyplanner.android.feature.auth.ui.family.PairingCodeDialog
import de.familyplanner.android.feature.auth.ui.home.HomeScreen
import de.familyplanner.android.feature.auth.ui.home.HomeUiState
import de.familyplanner.android.feature.auth.ui.pair.PairScreen
import de.familyplanner.android.feature.auth.ui.pair.PairUiState
import de.familyplanner.android.feature.auth.ui.setup.SetupScreen
import de.familyplanner.android.feature.auth.ui.setup.SetupUiState
import de.familyplanner.android.feature.auth.ui.welcome.WelcomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { FamilyPlannerTheme(content = content) }

    @Test
    fun `REQ-AUTH-002 pair screen shows pairing error`() {
        show { PairScreen(PairUiState(code = "123456", deviceName = "Pixel", error = AppError.InvalidPairingCode), {}, {}) }

        compose.onNodeWithText("Code ungültig oder abgelaufen.").assertIsDisplayed()
    }

    @Test
    fun `welcome screen shows signed out notice only when requested`() {
        var notice by androidx.compose.runtime.mutableStateOf(true)
        show { WelcomeScreen(signedOutNotice = notice, onPair = {}, onSetup = {}) }

        compose.onNodeWithText("Dieses Gerät wurde abgemeldet. Bitte neu koppeln.").assertIsDisplayed()
        notice = false
        compose.waitForIdle()
        compose.onNodeWithText("Dieses Gerät wurde abgemeldet. Bitte neu koppeln.").assertDoesNotExist()
    }

    @Test
    fun `REQ-AUTH-002 setup conflict offers pairing instead`() {
        var paired = false
        show {
            SetupScreen(SetupUiState(error = AppError.Conflict("x")), {}, {}, onPair = { paired = true })
        }

        compose.onNodeWithText("Bereits eingerichtet – bitte mit Code verbinden.").assertIsDisplayed()
        compose.onNodeWithText("Mit Code verbinden").performClick()
        assertEquals(true, paired)
    }

    @Test
    fun `REQ-TASK-002 home shows greeting, role and today slot`() {
        show { HomeScreen(HomeUiState(session = aSession), todayContent = { Text("Slot-Inhalt") }) }

        compose.onNodeWithText("Hallo Lea 👋").assertIsDisplayed()
        compose.onNodeWithText("Kind").assertIsDisplayed()
        compose.onNodeWithText("Slot-Inhalt").assertIsDisplayed()
    }

    @Test
    fun `REQ-AUTH-002 home offers no family management to children`() {
        show { HomeScreen(HomeUiState(session = aSession)) }

        compose.onNodeWithText("Familie verwalten").assertDoesNotExist()
    }

    @Test
    fun `REQ-TASK-002 home shows offline hint`() {
        show { HomeScreen(HomeUiState(session = aSession.copy(role = Role.PARENT), isOffline = true)) }

        compose.onNodeWithText("Elternteil").assertIsDisplayed()
        compose.onNodeWithText("Offline – es werden die zuletzt bekannten Daten gezeigt.").assertIsDisplayed()
    }

    @Test
    fun `family screen as main page has no back button`() {
        show { FamilyScreen(FamilyUiState(), {}, onBack = null) }

        compose.onNodeWithText("Zurück").assertDoesNotExist()
    }

    @Test
    fun `family screen with back handler shows back button`() {
        show { FamilyScreen(FamilyUiState(), {}, onBack = {}) }

        compose.onNodeWithText("Zurück").assertIsDisplayed()
    }

    @Test
    fun `REQ-AUTH-002 pairing code dialog shows code and expiry in Berlin time`() {
        val dialog = IssuedCodeDialog("Lea", IssuedPairingCode("483920", Instant.parse("2026-10-12T16:42:00Z")))
        show { PairingCodeDialog(dialog, onDismiss = {}) }

        compose.onNodeWithText("483920 · gültig bis 18:42").assertIsDisplayed()
        compose.onNodeWithText("Ein neuer Code macht den alten ungültig.").assertIsDisplayed()
    }

    @Test
    fun `REQ-AUTH-003 removing a device asks for confirmation first`() {
        val device = FamilyDevice("d-2", "m-1", "Tablet", null)
        val events = mutableListOf<FamilyEvent>()
        var state by androidx.compose.runtime.mutableStateOf(
            FamilyUiState(members = listOf(FamilyMember("m-1", "Lea", Role.CHILD)), devices = listOf(device), ownDeviceId = "d-1"),
        )
        show {
            FamilyScreen(state, onEvent = {
                events += it
                if (it is FamilyEvent.RequestRevoke) {
                    state =
                        state.copy(pendingRevoke = it.device)
                }
            }, onBack = {})
        }

        compose.onNodeWithText("Tablet (Lea)").assertIsDisplayed()
        compose.onNodeWithContentDescription("Gerät entfernen").performClick()

        compose.onNodeWithText("Gerät entfernen?").assertIsDisplayed()
        compose.onNodeWithText("Tablet wird abgemeldet.").assertIsDisplayed()
        assertEquals(listOf<FamilyEvent>(FamilyEvent.RequestRevoke(device)), events)
    }

    @Test
    fun `REQ-AUTH-003 removing own device warns about sign out`() {
        val own = FamilyDevice("d-1", "m-1", "Pixel", null)
        show { FamilyScreen(FamilyUiState(devices = listOf(own), ownDeviceId = "d-1", pendingRevoke = own), {}, onBack = {}) }

        compose.onNodeWithText("Du entfernst dein eigenes Gerät und wirst abgemeldet.").assertIsDisplayed()
    }

    @Test
    fun `forbidden error is shown to children opening the family screen`() {
        show { FamilyScreen(FamilyUiState(error = AppError.Forbidden), {}, onBack = {}) }

        compose.onNodeWithText("Nur für Eltern.").assertIsDisplayed()
    }
}
