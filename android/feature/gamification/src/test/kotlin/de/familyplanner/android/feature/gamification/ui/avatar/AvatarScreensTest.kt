package de.familyplanner.android.feature.gamification.ui.avatar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarEditorData
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarProfile
import de.familyplanner.android.feature.gamification.domain.avatar.rubrics
import de.familyplanner.android.feature.gamification.fakes.freeLook
import de.familyplanner.android.feature.gamification.fakes.goal
import de.familyplanner.android.feature.gamification.fakes.reward
import de.familyplanner.android.feature.gamification.fakes.sampleCatalog
import de.familyplanner.android.feature.gamification.ui.today.TodayAvatarCard
import de.familyplanner.android.feature.gamification.ui.today.TodayAvatarUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AvatarScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun state(
        balance: Int = 40,
        draft: de.familyplanner.android.core.ui.avatar.AvatarLook = freeLook,
        focusedId: String? = null,
        confirmId: String? = null,
        goalReward: Boolean = false,
    ): AvatarEditorUiState {
        val items = sampleCatalog().slots.flatMap { it.items }
        val data =
            AvatarEditorData(
                sampleCatalog(),
                AvatarProfile("c-1", null, emptySet()),
                goal(if (goalReward) reward("r-1", price = 200) else null, balance),
                freeLook,
            )
        return AvatarEditorUiState(
            memberId = "c-1",
            data = data,
            draft = draft,
            balance = balance,
            focused = items.firstOrNull { it.id == focusedId },
            confirmPurchase = items.firstOrNull { it.id == confirmId },
            isLoading = false,
            selectedRubric = sampleCatalog().rubrics().indexOfFirst { it.slot.key == "top" },
        )
    }

    private fun show(
        state: AvatarEditorUiState,
        onEvent: (AvatarEditorEvent) -> Unit = {},
    ) = compose.setContent { FamilyPlannerTheme { AvatarEditorScreen(state, onEvent, onBack = {}) } }

    @Test
    fun `REQ-GAME-031 editor shows rubric tabs and missing points`() {
        show(state(balance = 17, focusedId = "top.bigHair"))

        compose.onNodeWithText("Frisur").assertIsDisplayed()
        compose.onNodeWithText("Kopfbedeckung").assertIsDisplayed()
        compose.onNodeWithText("Noch 3 Punkte").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-031 buy button is shown for an affordable item`() {
        val events = mutableListOf<AvatarEditorEvent>()
        show(state(focusedId = "top.bigHair"), events::add)

        compose.onNodeWithText("Kaufen für 20 P").performScrollTo().performClick()

        assertEquals(AvatarEditorEvent.AskPurchase, events.single())
    }

    @Test
    fun `REQ-GAME-041 purchase dialog warns about the savings goal`() {
        show(state(balance = 120, confirmId = "top.bigHair", goalReward = true))

        compose.onNodeWithText("„Große Haare“ kaufen?").assertIsDisplayed()
        compose.onNodeWithText("Achtung", substring = true).assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-041 purchase dialog without goal has no warning`() {
        show(state(confirmId = "top.bigHair"))

        compose.onNodeWithText("Kostet 20 Punkte. Danach hast du noch 20.").assertIsDisplayed()
        compose.onNodeWithText("Achtung", substring = true).assertDoesNotExist()
    }

    @Test
    fun `REQ-GAME-031 save is disabled for an unowned item`() {
        show(state(draft = freeLook.copy(top = "top.bigHair")))

        compose.onNodeWithText("Speichern").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `REQ-GAME-030 save is enabled for the default look`() {
        show(state())

        compose.onNodeWithText("Speichern").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `REQ-GAME-030 editor shows the Avataaars credit`() {
        show(state())

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Avataaars", substring = true))
        compose.onNodeWithText("Avataaars", substring = true).assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-030 my avatar card invites to set up`() {
        compose.setContent { FamilyPlannerTheme { MyAvatarCard(look = null, name = "Lea", enabled = true, onClick = {}) } }

        compose.onNodeWithText("Richte deinen Avatar ein").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-030 my avatar card is not clickable offline`() {
        compose.setContent { FamilyPlannerTheme { MyAvatarCard(look = freeLook, name = "Lea", enabled = false, onClick = {}) } }

        compose.onNodeWithText("Avatar & Shop").assertIsDisplayed()
    }

    @Test
    fun `REQ-GAME-030 today card shows only for children`() {
        compose.setContent { FamilyPlannerTheme { TodayAvatarCard(TodayAvatarUiState(isChild = false), {}) } }

        compose.onNodeWithText("Richte deinen Avatar ein").assertDoesNotExist()
    }

    @Test
    fun `REQ-GAME-030 today card for a child opens the editor`() {
        var opened = false
        compose.setContent { FamilyPlannerTheme { TodayAvatarCard(TodayAvatarUiState(isChild = true, name = "Lea"), { opened = true }) } }

        compose.onNodeWithText("Richte deinen Avatar ein").performClick()

        assertEquals(true, opened)
    }
}
