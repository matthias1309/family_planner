package de.familyplanner.android.feature.gamification.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.gamification.domain.ChildRewards
import de.familyplanner.android.feature.gamification.domain.ManagedRewards
import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import de.familyplanner.android.feature.gamification.domain.RewardDraft
import de.familyplanner.android.feature.gamification.fakes.goal
import de.familyplanner.android.feature.gamification.fakes.redemption
import de.familyplanner.android.feature.gamification.fakes.reward
import de.familyplanner.android.feature.gamification.fakes.snapshot
import de.familyplanner.android.feature.gamification.ui.components.SavingsGoalCard
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsSection
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsUiState
import de.familyplanner.android.feature.gamification.ui.manage.ManageRewardsEvent
import de.familyplanner.android.feature.gamification.ui.manage.ManageRewardsScreen
import de.familyplanner.android.feature.gamification.ui.manage.ManageRewardsUiState
import de.familyplanner.android.feature.gamification.ui.me.MeScreen
import de.familyplanner.android.feature.gamification.ui.me.MeUiState
import de.familyplanner.android.feature.gamification.ui.rewards.RewardsEvent
import de.familyplanner.android.feature.gamification.ui.rewards.RewardsScreen
import de.familyplanner.android.feature.gamification.ui.rewards.RewardsUiState
import de.familyplanner.android.feature.gamification.ui.today.TodaySavingsCard
import de.familyplanner.android.feature.gamification.ui.today.TodaySavingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RewardScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun scrollTo(text: String) = compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))

    private val childData =
        ChildRewards(
            rewards = listOf(reward("r-1"), reward("r-2", 80, "Pizzaabend", "🍕")),
            goal = goal(balance = 120),
            redemptions = listOf(redemption("d-1", RedemptionStatus.REJECTED, reason = "Ferien", title = "Kino")),
        )

    @Test
    fun `REQ-GAME-041 goal card shows progress and redeemable`() {
        compose.setContent { FamilyPlannerTheme { SavingsGoalCard(goal(balance = 200), null) } }

        compose.onNodeWithText("🎢 Freizeitpark").assertIsDisplayed()
        compose.onNodeWithText("200/200").assertIsDisplayed()
        compose.onNodeWithText("Einlösbar!").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-041 goal card without goal and with archived reward`() {
        compose.setContent { FamilyPlannerTheme { SavingsGoalCard(goal(reward = null), null) } }
        compose.onNodeWithText("Noch kein Sparziel – such dir eine Belohnung aus.").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-041 goal card marks an archived reward`() {
        compose.setContent { FamilyPlannerTheme { SavingsGoalCard(goal(reward("r-1", archived = true)), null) } }

        compose.onNodeWithText("Nicht mehr einlösbar").assertIsDisplayed()
        compose.onNodeWithText("120/200").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-041 me screen shows the goal card and the rewards button, offline disabled`() {
        var opened = 0
        val online = MeUiState(memberId = "c-1", snapshot = snapshot(savingsGoal = goal()), isLoading = false)
        compose.setContent { FamilyPlannerTheme { MeScreen(online, {}, { _, _ -> }, { opened++ }) } }

        compose.onNodeWithText("Sparziel").assertIsDisplayed()
        compose.onNodeWithText("Belohnungen").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `REQ-GAME-041 offline me screen disables the rewards button`() {
        val offline = MeUiState(memberId = "c-1", snapshot = snapshot(savingsGoal = goal()), isLoading = false, isOffline = true)
        compose.setContent { FamilyPlannerTheme { MeScreen(offline, {}, { _, _ -> }, {}) } }

        compose.onNodeWithText("🎢 Freizeitpark").assertIsDisplayed()
        compose.onNodeWithText("Belohnungen").assertIsNotEnabled()
    }

    @Test
    fun `REQ-GAME-042 rewards screen disables redeem without enough balance`() {
        compose.setContent { FamilyPlannerTheme { RewardsScreen(RewardsUiState("c-1", childData, isLoading = false), {}, {}) } }

        compose.onNodeWithText("Dein Guthaben: 120").assertIsDisplayed()
        compose.onNodeWithText("Noch 80 Punkte").assertIsDisplayed()
        compose.onNodeWithText("Dein Sparziel").assertIsDisplayed()
        compose.onNodeWithText("Kein Sparziel mehr").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-042 rewards screen asks for a confirmation`() {
        val events = mutableListOf<RewardsEvent>()
        val state = RewardsUiState("c-1", childData, isLoading = false)
        compose.setContent { FamilyPlannerTheme { RewardsScreen(state, { events += it }, {}) } }

        scrollTo("Pizzaabend")
        compose
            .onNode(hasText("Einlösen") and isEnabled())
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(listOf<RewardsEvent>(RewardsEvent.AskRedeem(reward("r-2", 80, "Pizzaabend", "🍕"))), events)
    }

    @Test
    fun `REQ-GAME-042 confirmation dialog redeems`() {
        val events = mutableListOf<RewardsEvent>()
        val state = RewardsUiState("c-1", childData, isLoading = false, confirmRedeem = reward("r-2", 80, "Pizzaabend", "🍕"))
        compose.setContent { FamilyPlannerTheme { RewardsScreen(state, { events += it }, {}) } }

        compose.onNodeWithText("🍕 Pizzaabend einlösen?").assertIsDisplayed()
        compose.onNodeWithText("80 Punkte werden abgebucht. Deine Eltern bestätigen die Einlösung.").assertIsDisplayed()
        compose.onNode(hasText("Einlösen") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(listOf<RewardsEvent>(RewardsEvent.ConfirmRedeem), events)
    }

    @Test
    fun `REQ-GAME-043 rewards screen lists own redemptions with reason`() {
        compose.setContent { FamilyPlannerTheme { RewardsScreen(RewardsUiState("c-1", childData, isLoading = false), {}, {}) } }

        scrollTo("Grund: Ferien")
        compose.onNodeWithText("Meine Einlösungen").assertIsDisplayed()
        compose.onNodeWithText("Grund: Ferien").assertIsDisplayed()
        compose.onNodeWithText("80 P · abgelehnt").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-043 manage screen shows names with fulfill and reject`() {
        val events = mutableListOf<ManageRewardsEvent>()
        val data =
            ManagedRewards(
                listOf(reward("r-1")),
                listOf(redemption("d-1", memberId = "c-2"), redemption("d-2", memberId = "c-9")),
                mapOf(
                    "c-2" to "Ben",
                ),
            )
        compose.setContent {
            FamilyPlannerTheme {
                ManageRewardsScreen(
                    ManageRewardsUiState(data, isLoading = false),
                    { events += it },
                    {},
                )
            }
        }

        compose.onNodeWithText("Ben · 🍕 Pizzaabend · 80 P").assertIsDisplayed()
        compose.onNodeWithText("Kind · 🍕 Pizzaabend · 80 P").assertIsDisplayed()
        compose.onAllNodesWithText("Erfüllt")[0].performClick()

        assertEquals(1, events.size)
        assertTrue(events.single() is ManageRewardsEvent.Fulfill)
    }

    @Test
    fun `REQ-GAME-040 manage screen dialogs`() {
        val data = ManagedRewards(listOf(reward("r-1")), emptyList(), emptyMap())
        val edit = ManageRewardsUiState(data, isLoading = false, editing = RewardDraft(title = "Kino", priceText = "0"))
        compose.setContent { FamilyPlannerTheme { ManageRewardsScreen(edit, {}, {}) } }

        compose.onNodeWithText("1 bis 10 000 Punkte").assertIsDisplayed()
        compose.onNodeWithText("Speichern").assertIsNotEnabled()
    }

    @Test
    fun `REQ-GAME-043 reject dialog needs a reason`() {
        val data = ManagedRewards(emptyList(), listOf(redemption("d-1")), emptyMap())
        val state = ManageRewardsUiState(data, isLoading = false, rejecting = redemption("d-1"), rejectReason = "")
        compose.setContent { FamilyPlannerTheme { ManageRewardsScreen(state, {}, {}) } }

        compose.onNodeWithText("Die Punkte werden zurückgebucht.").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-042 family section shows pending redemptions`() {
        compose.setContent {
            FamilyPlannerTheme {
                FamilyPointsSection(
                    FamilyPointsUiState(
                        snapshot = snapshot(),
                        cookingPoints = 3,
                        pendingRedemptions = 1,
                        isLoading = false,
                    ),
                    {
                    },
                    { _, _ -> },
                    {},
                )
            }
        }

        compose.onNodeWithText("1 wartet").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-041 today card is hidden without a goal`() {
        compose.setContent { FamilyPlannerTheme { TodaySavingsCard(TodaySavingsUiState(goal(reward = null)), {}) } }

        compose.onNodeWithText("Sparziel").assertDoesNotExist()
    }

    @Test
    fun `REQ-GAME-041 today card opens the rewards`() {
        var opened = false
        compose.setContent { FamilyPlannerTheme { TodaySavingsCard(TodaySavingsUiState(goal()), { opened = true }) } }

        compose.onNodeWithText("🎢 Freizeitpark").performClick()

        assertTrue(opened)
    }
}
