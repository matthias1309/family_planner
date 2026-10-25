package de.familyplanner.android.feature.gamification.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.gamification.domain.EntryKind
import de.familyplanner.android.feature.gamification.domain.RankedChild
import de.familyplanner.android.feature.gamification.fakes.account
import de.familyplanner.android.feature.gamification.fakes.entry
import de.familyplanner.android.feature.gamification.fakes.leaderboard
import de.familyplanner.android.feature.gamification.fakes.snapshot
import de.familyplanner.android.feature.gamification.ui.account.BookingDraft
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountArgs
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountEvent
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountScreen
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountUiState
import de.familyplanner.android.feature.gamification.ui.components.LeaderboardList
import de.familyplanner.android.feature.gamification.ui.components.LedgerList
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsEvent
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsSection
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsUiState
import de.familyplanner.android.feature.gamification.ui.me.MeEvent
import de.familyplanner.android.feature.gamification.ui.me.MeScreen
import de.familyplanner.android.feature.gamification.ui.me.MeUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `REQ-GAME-014 leaderboard is clickable and shows ranks`() {
        var clicked: RankedChild? = null
        compose.setContent { FamilyPlannerTheme { LeaderboardList(leaderboard, "c-1") { clicked = it } } }

        compose.onNodeWithText("Ben").assertIsDisplayed()
        compose.onNodeWithText("Lea").performClick()

        assertEquals("c-1", clicked?.memberId)
    }

    @Test
    fun `REQ-GAME-014 empty leaderboard and ledger show hints`() {
        compose.setContent {
            FamilyPlannerTheme {
                LeaderboardList(emptyList(), null, null)
                LedgerList(emptyList())
            }
        }

        compose.onNodeWithText("Noch keine Kinder in der Familie").assertIsDisplayed()
        compose.onNodeWithText("Noch keine Punkte – erledige eine Aufgabe oder koche.").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-012 amounts are signed and kinds are labelled`() {
        assertEquals("+3", formatAmount(3))
        assertEquals("−2", formatAmount(-2))
        compose.setContent {
            FamilyPlannerTheme {
                LedgerList(listOf(entry("a", 3, "Bad putzen"), entry("b", -2, "Fehler", EntryKind.MANUAL, note = "Doppelt gebucht")))
            }
        }

        compose.onNodeWithText("+3").assertIsDisplayed()
        compose.onNodeWithText("−2").assertIsDisplayed()
        compose.onNodeWithText("Korrektur").assertIsDisplayed()
        compose.onNodeWithText("Doppelt gebucht").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-012 me screen shows tiles, rank, leaderboard and history`() {
        compose.setContent {
            FamilyPlannerTheme { MeScreen(MeUiState(memberId = "c-1", snapshot = snapshot(), isLoading = false), {}) }
        }

        compose.onNodeWithText("Gesammelt").assertIsDisplayed()
        compose.onNodeWithText("Guthaben").assertIsDisplayed()
        compose.onNodeWithText("Platz 2 von 3").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Bad putzen"))
        compose.onNodeWithText("Bad putzen").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-012 me screen shows the offline notice`() {
        compose.setContent {
            FamilyPlannerTheme {
                MeScreen(MeUiState(memberId = "c-1", snapshot = snapshot(), isLoading = false, isOffline = true), { _: MeEvent -> })
            }
        }

        compose.onNodeWithText("Offline – Stand vom 25.10. 11:00").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-011 family section opens a child and changes cooking points`() {
        var opened: Pair<String, String>? = null
        val events = mutableListOf<FamilyPointsEvent>()
        compose.setContent {
            FamilyPlannerTheme {
                FamilyPointsSection(
                    FamilyPointsUiState(snapshot = snapshot(), cookingPoints = 3, isLoading = false),
                    { events += it },
                ) { id, name -> opened = id to name }
            }
        }

        compose.onNodeWithText("Mia").performClick()
        compose.onNodeWithText("Kochpunkte: 3").assertIsDisplayed()
        compose.onNodeWithText("Ändern").performClick()

        assertEquals("c-3" to "Mia", opened)
        assertEquals(listOf<FamilyPointsEvent>(FamilyPointsEvent.EditCookingPoints), events)
    }

    @Test
    fun `REQ-GAME-011 cooking points dialog stops at the limits`() {
        val events = mutableListOf<FamilyPointsEvent>()
        compose.setContent {
            FamilyPlannerTheme {
                FamilyPointsSection(
                    FamilyPointsUiState(snapshot = snapshot(), cookingPoints = 20, isLoading = false, editingCookingPoints = 20),
                    { events += it },
                ) { _, _ -> }
            }
        }

        compose.onNodeWithContentDescription("Mehr Punkte").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Weniger Punkte").performClick()
        compose.onNodeWithText("Speichern").performClick()

        assertEquals(listOf(FamilyPointsEvent.CookingPointsChanged(19), FamilyPointsEvent.SaveCookingPoints), events)
    }

    @Test
    fun `REQ-GAME-014 offline family section has no clickable children`() {
        var opened = false
        compose.setContent {
            FamilyPlannerTheme {
                FamilyPointsSection(
                    FamilyPointsUiState(snapshot = snapshot(), cookingPoints = 3, isOffline = true, isLoading = false),
                    {},
                ) { _, _ -> opened = true }
            }
        }

        compose.onNodeWithText("Mia").performClick()
        compose.onNodeWithText("Ändern").assertIsNotEnabled()

        assertEquals(false, opened)
    }

    private val args = ChildAccountArgs("c-1", "Lea")

    @Test
    fun `REQ-GAME-013 booking button opens the dialog`() {
        val events = mutableListOf<ChildAccountEvent>()
        compose.setContent {
            FamilyPlannerTheme {
                ChildAccountScreen(
                    ChildAccountUiState(args, account(entries = arrayOf(entry("a"))), isLoading = false),
                    { events += it },
                    {},
                )
            }
        }

        compose.onNodeWithText("Punktekonto Lea").assertIsDisplayed()
        compose.onNodeWithText("Punkte buchen").performClick()

        assertEquals(listOf<ChildAccountEvent>(ChildAccountEvent.StartBooking), events)
    }

    @Test
    fun `REQ-GAME-013 booking dialog enables Buchen only for valid input and counts characters`() {
        val events = mutableListOf<ChildAccountEvent>()
        compose.setContent {
            FamilyPlannerTheme {
                ChildAccountScreen(
                    ChildAccountUiState(args, account(), isLoading = false, booking = BookingDraft()),
                    { events += it },
                    {},
                )
            }
        }

        compose.onNodeWithText("0/200").assertIsDisplayed()
        compose.onNodeWithText("Buchen").assertIsNotEnabled()
        compose.onNodeWithText("Abziehen").performClick()
        compose.onNodeWithText("Betrag").performTextInput("2")

        assertEquals(ChildAccountEvent.BookingCreditChanged(false), events.first())
        assertEquals(ChildAccountEvent.BookingAmountChanged("2"), events.last())
    }

    @Test
    fun `REQ-GAME-013 valid booking can be submitted`() {
        val events = mutableListOf<ChildAccountEvent>()
        compose.setContent {
            FamilyPlannerTheme {
                ChildAccountScreen(
                    ChildAccountUiState(
                        args,
                        account(),
                        isLoading = false,
                        booking = BookingDraft(amountText = "12", reason = "Übernahme"),
                    ),
                    { events += it },
                    {},
                )
            }
        }

        compose.onNodeWithText("Buchen").assertIsEnabled().performClick()

        assertEquals(ChildAccountEvent.SubmitBooking, events.last())
    }
}
