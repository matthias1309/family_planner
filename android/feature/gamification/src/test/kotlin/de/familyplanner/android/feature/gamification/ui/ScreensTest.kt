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
import de.familyplanner.android.feature.gamification.domain.BadgeProgress
import de.familyplanner.android.feature.gamification.domain.EntryKind
import de.familyplanner.android.feature.gamification.domain.RankedChild
import de.familyplanner.android.feature.gamification.fakes.account
import de.familyplanner.android.feature.gamification.fakes.badge
import de.familyplanner.android.feature.gamification.fakes.entry
import de.familyplanner.android.feature.gamification.fakes.leaderboard
import de.familyplanner.android.feature.gamification.fakes.overview
import de.familyplanner.android.feature.gamification.fakes.snapshot
import de.familyplanner.android.feature.gamification.ui.account.BookingDraft
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountArgs
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountEvent
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountScreen
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountUiState
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesArgs
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesScreen
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesUiState
import de.familyplanner.android.feature.gamification.ui.components.BadgeList
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
            FamilyPlannerTheme { MeScreen(MeUiState(memberId = "c-1", snapshot = snapshot(), isLoading = false), {}, { _, _ -> }, {}) }
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
                MeScreen(
                    MeUiState(memberId = "c-1", snapshot = snapshot(), isLoading = false, isOffline = true),
                    { _: MeEvent -> },
                    { _, _ -> },
                    {},
                )
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
                    { id, name -> opened = id to name },
                    {},
                )
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
                    { _, _ -> },
                    {},
                )
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
                    { _, _ -> opened = true },
                    {},
                )
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

    private val badges =
        overview(
            badge("first-task", earned = true, title = "Erste Aufgabe"),
            badge("tasks-10"),
            badge("secret-1", secret = true, title = null, progress = null, hint = "Bleib richtig lange dran"),
        )

    @Test
    fun `REQ-GAME-021 badge list shows earned date, progress and masked secret badge`() {
        compose.setContent { FamilyPlannerTheme { BadgeList(badges.items) } }

        compose.onNodeWithText("Erste Aufgabe").assertIsDisplayed()
        compose.onNodeWithText("erhalten 25.10.").assertIsDisplayed()
        compose.onNodeWithText("7/10").assertIsDisplayed()
        compose.onNodeWithText("???").assertIsDisplayed()
        compose.onNodeWithText("Bleib richtig lange dran").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-021 empty badge list shows a hint`() {
        compose.setContent { FamilyPlannerTheme { BadgeList(emptyList()) } }

        compose.onNodeWithText("Noch keine Badges").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-021 open badge with reached target does not crash`() {
        compose.setContent { FamilyPlannerTheme { BadgeList(listOf(badge("tasks-10", progress = BadgeProgress(10, 10)))) } }

        compose.onNodeWithText("10/10").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-020 me screen shows the new badge dialog`() {
        var dismissed = false
        val state =
            MeUiState(
                memberId = "c-1",
                snapshot = snapshot(badges = badges),
                isLoading = false,
                newBadges = listOf(badges.items[0]),
            )
        compose.setContent { FamilyPlannerTheme { MeScreen(state, { dismissed = it == MeEvent.DismissNewBadges }, { _, _ -> }, {}) } }

        compose.onNodeWithText("Neues Badge!").assertIsDisplayed()
        compose.onNodeWithText("Super!").performClick()

        assertEquals(true, dismissed)
    }

    @Test
    fun `REQ-GAME-021 me screen opens badges of a sibling`() {
        var opened: Pair<String, String>? = null
        val state = MeUiState(memberId = "c-1", snapshot = snapshot(badges = badges), isLoading = false)
        compose.setContent { FamilyPlannerTheme { MeScreen(state, {}, { id, name -> opened = id to name }, {}) } }

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Ben"))
        compose.onNodeWithText("Ben").performClick()

        assertEquals("c-2" to "Ben", opened)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Badges 1/12"))
        compose.onNodeWithText("Badges 1/12").assertExists()
    }

    @Test
    fun `REQ-GAME-021 offline leaderboard is not clickable`() {
        var opened = false
        val state = MeUiState(memberId = "c-1", snapshot = snapshot(badges = badges), isLoading = false, isOffline = true)
        compose.setContent { FamilyPlannerTheme { MeScreen(state, {}, { _, _ -> opened = true }, {}) } }

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Ben"))
        compose.onNodeWithText("Ben").performClick()

        assertEquals(false, opened)
    }

    @Test
    fun `REQ-GAME-021 member badges screen shows name and count`() {
        val state = MemberBadgesUiState(MemberBadgesArgs("c-2", "Ben"), overview = badges, isLoading = false)
        compose.setContent { FamilyPlannerTheme { MemberBadgesScreen(state, {}, {}) } }

        compose.onNodeWithText("Badges von Ben").assertIsDisplayed()
        compose.onNodeWithText("Badges 1/12").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-021 child account screen shows badges`() {
        val state = ChildAccountUiState(ChildAccountArgs("c-1", "Lea"), account = account(), badges = badges, isLoading = false)
        compose.setContent { FamilyPlannerTheme { ChildAccountScreen(state, {}, {}) } }

        compose.onNodeWithText("Badges 1/12").assertExists()
    }
}
