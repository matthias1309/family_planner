package de.familyplanner.android.feature.tasks.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.tasks.domain.board.LoadBoardUseCase
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.fakes.FakeTaskRepository
import de.familyplanner.android.feature.tasks.fakes.InMemoryBoardCache
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.childSession
import de.familyplanner.android.feature.tasks.fakes.lane
import de.familyplanner.android.feature.tasks.fakes.networkDown
import de.familyplanner.android.feature.tasks.fakes.parentSession
import de.familyplanner.android.feature.tasks.ui.today.TodayTasksCard
import de.familyplanner.android.feature.tasks.ui.today.TodayTasksUiState
import de.familyplanner.android.feature.tasks.ui.today.TodayTasksViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TodayTasksTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val repository = FakeTaskRepository()
    private val cache = InMemoryBoardCache()

    private fun viewModel(session: de.familyplanner.android.core.data.session.Session): TodayTasksViewModel {
        val sessions = InMemorySessionStore(session)
        return TodayTasksViewModel(sessions, LoadBoardUseCase(repository, cache, sessions))
    }

    @Test
    fun `REQ-TASK-002 today shows open tasks and rejected count for a child`() {
        repository.boardResult =
            ApiResult.Success(
                listOf(
                    lane(
                        "c-1",
                        "Ben",
                        card("a", title = "A"),
                        card("b", title = "B", reason = "Nochmal"),
                        card("c", title = "C", status = TaskStatus.REVIEW),
                        card("d", title = "D"),
                        card("e", status = TaskStatus.DONE),
                    ),
                ),
            )

        val state = viewModel(childSession).state.value

        assertEquals(4, state.openCount)
        assertEquals(3, state.titles.size)
        assertEquals(1, state.rejectedCount)
        compose.setContent { FamilyPlannerTheme { TodayTasksCard(state, onOpenTasks = {}) } }
        compose.onNodeWithText("Deine Aufgaben").assertIsDisplayed()
        compose.onNodeWithText("4 offen").assertIsDisplayed()
        compose.onNodeWithText("1 zurückgewiesen").assertIsDisplayed()
    }

    @Test
    fun `REQ-TASK-002 today shows the review count for parents`() {
        repository.boardResult =
            ApiResult.Success(listOf(lane("c-1", "Ben", card("a", status = TaskStatus.REVIEW), card("b", status = TaskStatus.REVIEW))))

        val state = viewModel(parentSession).state.value
        var opened = false
        compose.setContent { FamilyPlannerTheme { TodayTasksCard(state, onOpenTasks = { opened = true }) } }

        compose.onNodeWithText("2 warten auf Abnahme").assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun `REQ-TASK-002 today covers the singular, the empty and the unavailable case`() {
        compose.setContent {
            FamilyPlannerTheme {
                androidx.compose.foundation.layout.Column {
                    TodayTasksCard(TodayTasksUiState(isLoading = false, isParent = true, reviewCount = 1), {})
                    TodayTasksCard(TodayTasksUiState(isLoading = false, isParent = true), {})
                    TodayTasksCard(TodayTasksUiState(isLoading = false), {})
                    TodayTasksCard(TodayTasksUiState(isLoading = false, unavailable = true), {})
                }
            }
        }

        compose.onNodeWithText("1 wartet auf Abnahme").assertIsDisplayed()
        compose.onNodeWithText("Nichts zur Abnahme.").assertIsDisplayed()
        compose.onNodeWithText("Alles erledigt 🎉").assertIsDisplayed()
        compose.onNodeWithText("Aufgaben gerade nicht erreichbar.").assertIsDisplayed()
    }

    @Test
    fun `REQ-TASK-002 today falls back to the stored board offline`() {
        cache.seed("c-1", lane("c-1", "Ben", card("a")))
        repository.boardResult = networkDown

        val state = viewModel(childSession).state.value

        assertEquals(1, state.openCount)
        assertTrue(!state.unavailable)
    }

    @Test
    fun `REQ-TASK-002 today is unavailable without a board`() {
        repository.boardResult = networkDown

        assertTrue(viewModel(childSession).state.value.unavailable)
    }
}
