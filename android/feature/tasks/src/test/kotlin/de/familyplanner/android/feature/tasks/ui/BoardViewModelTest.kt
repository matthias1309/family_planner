package de.familyplanner.android.feature.tasks.ui

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.tasks.domain.board.CardAction
import de.familyplanner.android.feature.tasks.domain.board.LoadBoardUseCase
import de.familyplanner.android.feature.tasks.domain.board.MoveCardUseCase
import de.familyplanner.android.feature.tasks.domain.board.RemoveCardUseCase
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.fakes.FakeTaskRepository
import de.familyplanner.android.feature.tasks.fakes.InMemoryBoardCache
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.childSession
import de.familyplanner.android.feature.tasks.fakes.lane
import de.familyplanner.android.feature.tasks.fakes.networkDown
import de.familyplanner.android.feature.tasks.fakes.parentSession
import de.familyplanner.android.feature.tasks.ui.board.BoardEvent
import de.familyplanner.android.feature.tasks.ui.board.BoardTab
import de.familyplanner.android.feature.tasks.ui.board.BoardViewModel
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeTaskRepository()
    private val cache = InMemoryBoardCache()
    private val todo = card("a", title = "Bad")
    private val review = card("r", status = TaskStatus.REVIEW, reportedAt = java.time.Instant.parse("2026-10-24T11:00:00Z"))

    private fun viewModel(session: de.familyplanner.android.core.data.session.Session = childSession): BoardViewModel {
        val sessions = InMemorySessionStore(session)
        return BoardViewModel(
            sessions,
            LoadBoardUseCase(repository, cache, sessions),
            MoveCardUseCase(repository, cache, sessions),
            RemoveCardUseCase(repository, cache, sessions),
        )
    }

    private fun seedBoard() {
        repository.boardResult = ApiResult.Success(listOf(lane("c-1", "Ben", todo, review), lane("c-2", "Lea", card("l", "c-2"))))
    }

    @Test
    fun `REQ-TASK-022 board view model reports a card and shows it in review`() {
        seedBoard()
        val vm = viewModel()

        vm.onEvent(BoardEvent.Act(todo, CardAction.REPORT))

        val lane = (vm.state.value.selectedTabContent as BoardTab.Lane).lane
        assertTrue(
            vm.state.value
                .sectionsOf(lane)
                .review
                .any { it.id == "a" },
        )
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun `REQ-TASK-021 a child sees the own lane first and parents the review tab`() {
        seedBoard()

        assertEquals(
            listOf("Ben", "Lea"),
            viewModel()
                .state.value.tabs
                .map { (it as BoardTab.Lane).lane.name },
        )
        val parentTabs = viewModel(parentSession).state.value.tabs
        assertEquals(BoardTab.Review, parentTabs.first())
        assertEquals(3, parentTabs.size)
    }

    @Test
    fun `REQ-TASK-023 conflict reloads the board and shows the message`() {
        seedBoard()
        val vm = viewModel(parentSession)
        repository.moveResult = { ApiResult.Failure(AppError.Conflict("Die Aufgabe ist schon erledigt")) }
        val loads = repository.boardCalls

        vm.onEvent(BoardEvent.Act(review, CardAction.ACCEPT))

        assertEquals(AppError.Conflict("Die Aufgabe ist schon erledigt"), vm.state.value.actionError)
        assertEquals(loads + 1, repository.boardCalls)
    }

    @Test
    fun `REQ-TASK-023 reject dialog sends optional reason`() {
        seedBoard()
        val vm = viewModel(parentSession)

        vm.onEvent(BoardEvent.Act(review, CardAction.REJECT))
        assertEquals(review, vm.state.value.rejecting)
        vm.onEvent(BoardEvent.ConfirmReject)

        assertEquals(listOf<Pair<String, String?>>("r" to null), repository.rejections)
        assertNull(vm.state.value.rejecting)
    }

    @Test
    fun `REQ-TASK-023 reject dialog passes the typed reason`() {
        seedBoard()
        val vm = viewModel(parentSession)

        vm.onEvent(BoardEvent.Act(review, CardAction.REJECT))
        vm.onEvent(BoardEvent.RejectReasonChanged("Unter dem Bett"))
        vm.onEvent(BoardEvent.ConfirmReject)

        assertEquals(listOf<Pair<String, String?>>("r" to "Unter dem Bett"), repository.rejections)
    }

    @Test
    fun `REQ-TASK-024 direct accept and remove ask for confirmation first`() {
        seedBoard()
        val vm = viewModel(parentSession)

        vm.onEvent(BoardEvent.Act(todo, CardAction.ACCEPT_DIRECTLY))
        assertEquals(todo to CardAction.ACCEPT_DIRECTLY, vm.state.value.confirming)
        assertTrue(repository.moves.isEmpty())
        vm.onEvent(BoardEvent.DismissDialog)
        assertNull(vm.state.value.confirming)

        vm.onEvent(BoardEvent.Act(todo, CardAction.REMOVE))
        vm.onEvent(BoardEvent.ConfirmAction)

        assertEquals(listOf("a"), repository.removed)
        assertTrue(
            vm.state.value.lanes
                .flatMap { it.cards }
                .none { it.id == "a" },
        )
    }

    @Test
    fun `REQ-TASK-021 offline board disables actions and shows sync time`() {
        cache.seed("c-1", lane("c-1", "Ben", todo))
        repository.boardResult = networkDown

        val vm = viewModel()

        assertTrue(vm.state.value.isOffline)
        assertEquals(emptyList(), vm.state.value.actionsFor(todo))
        assertIs<BoardTab.Lane>(vm.state.value.selectedTabContent)
        assertTrue(vm.state.value.syncedAt != null)
    }

    @Test
    fun `REQ-TASK-021 failed load without a stored board shows the error`() {
        repository.boardResult = networkDown

        val vm = viewModel()

        assertEquals(AppError.Network, vm.state.value.error)
        assertTrue(
            vm.state.value.tabs
                .isEmpty(),
        )
    }

    @Test
    fun `REQ-TASK-021 selected tab is clamped when a lane disappears`() {
        seedBoard()
        val vm = viewModel(parentSession)
        vm.onEvent(BoardEvent.SelectTab(2))
        repository.boardResult = ApiResult.Success(listOf(lane("c-1", "Ben")))

        vm.onEvent(BoardEvent.Refresh)

        assertEquals(1, vm.state.value.selectedIndex)
        assertEquals(1, vm.state.value.selectedTab)
    }
}
