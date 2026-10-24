package de.familyplanner.android.feature.tasks.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.tasks.domain.board.CardAction
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.lane
import de.familyplanner.android.feature.tasks.fakes.noon
import de.familyplanner.android.feature.tasks.ui.board.BoardEvent
import de.familyplanner.android.feature.tasks.ui.board.BoardScreen
import de.familyplanner.android.feature.tasks.ui.board.BoardUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BoardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<BoardEvent>()
    private var assigned: String? = "unset"
    private var history: Pair<String, String>? = null
    private var catalogOpened = false

    private val bad = card("a", title = "Bad putzen", points = 3, emoji = "🛁")
    private val rejected = card("b", title = "Zimmer", reason = "Unter dem Bett")
    private val review = card("r", title = "Müll", status = TaskStatus.REVIEW, reportedAt = noon)
    private val done = card("d", title = "Spülmaschine", status = TaskStatus.DONE, doneAt = noon)
    private val lanes =
        listOf(
            lane("c-1", "Ben", bad, rejected, review, done),
            lane("c-2", "Lea", card("l", "c-2", title = "Lea-Aufgabe", status = TaskStatus.REVIEW, reportedAt = noon)),
        )

    private fun show(state: BoardUiState) =
        compose.setContent {
            FamilyPlannerTheme {
                BoardScreen(
                    state,
                    { events += it },
                    { catalogOpened = true },
                    { assigned = it },
                    { id, name -> history = id to name },
                )
            }
        }

    private fun child(offline: Boolean = false) =
        BoardUiState(memberId = "c-1", isParent = false, lanes = lanes, isOffline = offline, syncedAt = noon)

    private fun parent(tab: Int = 0) = BoardUiState(memberId = "p-1", isParent = true, lanes = lanes, selectedTab = tab)

    @Test
    fun `REQ-TASK-021 child sees the own lane first with report button and rejection reason`() {
        show(child())

        compose.onNodeWithText("Ben").assertIsDisplayed()
        compose.onNodeWithText("Lea").assertIsDisplayed()
        compose.onNodeWithText("Todo").assertIsDisplayed()
        compose.onNodeWithText("🛁 Bad putzen").assertIsDisplayed()
        compose.onNodeWithText("Zurückgewiesen: „Unter dem Bett“").assertIsDisplayed()
        compose.scrollTo("Zurückziehen").assertIsDisplayed()
        compose.scrollTo("Spülmaschine").assertIsDisplayed()
        compose.scrollTo("Erledigt (7 Tage)").assertIsDisplayed()
        compose.onNodeWithContentDescription("Katalog").assertDoesNotExist()
        compose.onNodeWithText("Zuweisen").assertDoesNotExist()
    }

    @Test
    fun `REQ-TASK-022 child can report a card`() {
        show(child())

        compose.onAllNodesWithTextFirst("Erledigt").performClick()

        assertTrue(events.single().let { it is BoardEvent.Act && it.action == CardAction.REPORT })
    }

    @Test
    fun `REQ-TASK-021 parent sees the review tab with count, accept and reject buttons`() {
        show(parent())

        compose.onNodeWithText("Zur Abnahme (2)").assertIsDisplayed()
        compose.onNodeWithText("Ben · gemeldet 24.10. 12:00").assertIsDisplayed()
        compose.onNodeWithText("Lea · gemeldet 24.10. 12:00").assertIsDisplayed()
        compose.onAllNodesWithTextFirst("Abnehmen").performClick()
        compose.onAllNodesWithTextFirst("Zurückweisen").performClick()

        assertEquals(listOf(CardAction.ACCEPT, CardAction.REJECT), events.map { (it as BoardEvent.Act).action })
    }

    @Test
    fun `REQ-TASK-021 parent can switch to a child lane and use the overflow menu`() {
        show(parent(tab = 1))

        compose.onAllNodesWithTextFirst("Weitere Aktionen", byDescription = true).performClick()
        compose.onNodeWithText("Direkt abnehmen").performClick()

        assertEquals(CardAction.ACCEPT_DIRECTLY, (events.single() as BoardEvent.Act).action)
    }

    @Test
    fun `REQ-TASK-021 tapping a tab selects it`() {
        show(parent())

        compose.onNodeWithText("Lea").performClick()

        assertEquals(BoardEvent.SelectTab(2), events.single())
    }

    @Test
    fun `REQ-TASK-020 parent opens assign for the current lane and the catalog`() {
        show(parent(tab = 1))

        compose.onNodeWithText("Zuweisen", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Katalog").performClick()

        assertEquals("c-1", assigned)
        assertTrue(catalogOpened)
    }

    @Test
    fun `REQ-TASK-025 history button opens the history of the lane`() {
        show(child())

        compose.scrollTo("Verlauf anzeigen").performClick()

        assertEquals("c-1" to "Ben", history)
    }

    @Test
    fun `REQ-TASK-021 offline board shows the hint and no buttons`() {
        show(child(offline = true))

        compose.onNodeWithText("Offline – Stand vom 24.10. 12:00").assertIsDisplayed()
        compose.onNodeWithText("Zurückziehen").assertDoesNotExist()
    }

    @Test
    fun `REQ-TASK-021 empty family shows a hint, errors are displayed`() {
        show(BoardUiState(memberId = "p-1", isParent = true, error = null))
        compose.onNodeWithText("Noch keine Kinder in der Familie.").assertIsDisplayed()
    }

    @Test
    fun `REQ-TASK-021 load error is displayed instead of the empty hint`() {
        show(BoardUiState(memberId = "p-1", isParent = true, error = AppError.Server, actionError = AppError.Network))

        compose.onNodeWithText("Noch keine Kinder in der Familie.").assertDoesNotExist()
    }

    @Test
    fun `REQ-TASK-023 reject dialog offers an optional reason and confirms without text`() {
        show(parent().copy(rejecting = review, rejectReason = ""))

        compose.onNodeWithText("Aufgabe zurückweisen?").assertIsDisplayed()
        compose.onNodeWithText("Begründung (optional)").assertIsDisplayed()
        compose.onAllNodesWithTextLast("Zurückweisen").performClick()

        assertEquals(BoardEvent.ConfirmReject, events.single())
    }

    @Test
    fun `REQ-TASK-024 confirmation dialogs name the card`() {
        show(parent().copy(confirming = bad to CardAction.REMOVE))

        compose.onNodeWithText("Aufgabe entfernen?").assertIsDisplayed()
        compose.onNodeWithText("„Bad putzen“ wird entfernt. Das lässt sich nicht rückgängig machen.").assertIsDisplayed()
        compose.onNodeWithText("Entfernen").performClick()

        assertEquals(BoardEvent.ConfirmAction, events.single())
    }

    @Test
    fun `REQ-TASK-022 buttons are disabled while an action runs`() {
        show(child().copy(isBusy = true))

        compose.onAllNodesWithTextFirst("Erledigt").assertIsNotEnabled()
    }
}
