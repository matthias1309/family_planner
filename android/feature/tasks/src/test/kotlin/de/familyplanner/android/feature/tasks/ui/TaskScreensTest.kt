package de.familyplanner.android.feature.tasks.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.noon
import de.familyplanner.android.feature.tasks.ui.assign.AssignChild
import de.familyplanner.android.feature.tasks.ui.assign.AssignEvent
import de.familyplanner.android.feature.tasks.ui.assign.AssignScreen
import de.familyplanner.android.feature.tasks.ui.assign.AssignUiState
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogEditEvent
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogEditScreen
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogEditUiState
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogScreen
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogUiState
import de.familyplanner.android.feature.tasks.ui.history.HistoryScreen
import de.familyplanner.android.feature.tasks.ui.history.HistoryUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TaskScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { FamilyPlannerTheme(content = content) }

    private val catalog = listOf(CatalogTask("k-1", "Bad putzen", "🛁", 3), CatalogTask("k-2", "Müll", null, 1))
    private val children = listOf(AssignChild("c-1", "Ben"), AssignChild("c-2", "Lea"))

    @Test
    fun `REQ-TASK-020 assign screen shows checks, counter and submits`() {
        val events = mutableListOf<AssignEvent>()
        show {
            AssignScreen(
                AssignUiState(
                    children = children,
                    catalog = catalog,
                    memberId = "c-1",
                    selected = setOf("k-1", "k-2"),
                    isLoading = false,
                ),
                { events += it },
                {},
                {},
            )
        }

        compose.onNodeWithText("🛁 Bad putzen").assertIsDisplayed()
        compose.onNodeWithText("3 P.").assertIsDisplayed()
        compose.onNodeWithText("Zuweisen (2)").assertIsEnabled().performClick()
        compose.onNodeWithText("Lea").performClick()
        compose.onNodeWithText("Müll").performClick()

        assertEquals(
            listOf<AssignEvent>(AssignEvent.Submit, AssignEvent.SelectChild("c-2"), AssignEvent.Toggle("k-2")),
            events,
        )
    }

    @Test
    fun `REQ-TASK-020 assign button is disabled without selection or with too many`() {
        show { AssignScreen(AssignUiState(children = children, catalog = catalog, memberId = "c-1", isLoading = false), {}, {}, {}) }

        compose.onNodeWithText("Zuweisen (0)").assertIsNotEnabled()
    }

    @Test
    fun `REQ-TASK-020 assign screen warns about more than 20 tasks`() {
        val selected = (1..21).map { "k-$it" }.toSet()
        show {
            AssignScreen(
                AssignUiState(children = children, catalog = catalog, memberId = "c-1", selected = selected, isLoading = false),
                {},
                {},
                {},
            )
        }

        compose.onNodeWithText("Höchstens 20 Aufgaben auf einmal.").assertIsDisplayed()
        compose.onNodeWithText("Zuweisen (21)").assertIsNotEnabled()
    }

    @Test
    fun `REQ-TASK-020 empty catalog offers to open the catalog`() {
        var opened = false
        show { AssignScreen(AssignUiState(children = children, memberId = "c-1", isLoading = false), {}, { opened = true }, {}) }

        compose.onNodeWithText("Noch keine Aufgaben im Katalog.").assertIsDisplayed()
        compose.onNodeWithText("Katalog öffnen").performClick()

        assertTrue(opened)
    }

    @Test
    fun `REQ-TASK-010 catalog screen lists tasks and opens one`() {
        var opened: String? = null
        var created = false
        show { CatalogScreen(CatalogUiState(catalog, isLoading = false), { opened = it }, { created = true }, {}) }

        compose.onNodeWithText("🛁 Bad putzen").performClick()
        compose.onNodeWithText("Neu", useUnmergedTree = true).performClick()

        assertEquals("k-1", opened)
        assertTrue(created)
    }

    @Test
    fun `REQ-TASK-010 empty catalog shows a hint and errors are displayed`() {
        show { CatalogScreen(CatalogUiState(isLoading = false), {}, {}, {}) }

        compose.onNodeWithText("Noch keine Aufgaben im Katalog.").assertIsDisplayed()
    }

    @Test
    fun `REQ-TASK-010 catalog editor shows counter and points stepper limits`() {
        val events = mutableListOf<CatalogEditEvent>()
        show { CatalogEditScreen(CatalogEditUiState(null, title = "Bad", points = 1), { events += it }, {}) }

        compose.onNodeWithText("Neue Aufgabe").assertIsDisplayed()
        compose.onNodeWithText("3/40").assertIsDisplayed()
        compose.onNodeWithContentDescription("Weniger Punkte").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mehr Punkte").assertIsEnabled().performClick()
        compose.onNodeWithText("Archivieren").assertDoesNotExist()
        compose.onNodeWithText("Speichern").performClick()

        assertEquals(listOf<CatalogEditEvent>(CatalogEditEvent.PointsChanged(2), CatalogEditEvent.Save), events)
    }

    @Test
    fun `REQ-TASK-010 catalog editor of an existing task archives after confirmation`() {
        val events = mutableListOf<CatalogEditEvent>()
        show { CatalogEditScreen(CatalogEditUiState("k-1", title = "Bad", points = 100, confirmingArchive = true), { events += it }, {}) }

        compose.onNodeWithContentDescription("Mehr Punkte").assertIsNotEnabled()
        compose.onNodeWithText("Aufgabe archivieren?").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Die Aufgabe kann danach nicht mehr zugewiesen werden. Bereits verteilte Karten bleiben.",
            ).assertIsDisplayed()
        compose.onAllNodesWithTextLast("Archivieren").performClick()

        assertEquals(listOf<CatalogEditEvent>(CatalogEditEvent.ConfirmArchive), events)
    }

    @Test
    fun `REQ-TASK-010 catalog editor shows a load error with retry`() {
        val events = mutableListOf<CatalogEditEvent>()
        show { CatalogEditScreen(CatalogEditUiState("k-1", loadError = AppError.Network), { events += it }, {}) }

        compose.onNodeWithText("Erneut versuchen").performClick()

        assertEquals(listOf<CatalogEditEvent>(CatalogEditEvent.Retry), events)
    }

    @Test
    fun `REQ-TASK-025 history shows done cards and the empty text`() {
        show {
            HistoryScreen(
                HistoryUiState("Ben", listOf(card("d", title = "Müll", status = TaskStatus.DONE, doneAt = noon)), isLoading = false),
                {},
            )
        }

        compose.onNodeWithText("Verlauf: Ben").assertIsDisplayed()
        compose.onNodeWithText("Müll").assertIsDisplayed()
        compose.onNodeWithText("erledigt am 24.10. 12:00").assertIsDisplayed()
    }

    @Test
    fun `REQ-TASK-025 history without cards shows the empty text`() {
        show { HistoryScreen(HistoryUiState("Ben", isLoading = false), {}) }

        compose.onNodeWithText("Noch nichts erledigt.").assertIsDisplayed()
    }
}
