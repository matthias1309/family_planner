package de.familyplanner.android.feature.tasks.ui

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.tasks.domain.board.AssignTasksUseCase
import de.familyplanner.android.feature.tasks.domain.board.LoadBoardUseCase
import de.familyplanner.android.feature.tasks.domain.board.LoadHistoryUseCase
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.domain.catalog.ArchiveCatalogTaskUseCase
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogUseCases
import de.familyplanner.android.feature.tasks.domain.catalog.LoadCatalogUseCase
import de.familyplanner.android.feature.tasks.domain.catalog.SaveCatalogTaskUseCase
import de.familyplanner.android.feature.tasks.fakes.FakeCatalogRepository
import de.familyplanner.android.feature.tasks.fakes.FakeTaskRepository
import de.familyplanner.android.feature.tasks.fakes.InMemoryBoardCache
import de.familyplanner.android.feature.tasks.fakes.benLane
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.leaLane
import de.familyplanner.android.feature.tasks.fakes.parentSession
import de.familyplanner.android.feature.tasks.ui.assign.AssignArgs
import de.familyplanner.android.feature.tasks.ui.assign.AssignEvent
import de.familyplanner.android.feature.tasks.ui.assign.AssignViewModel
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogEditArgs
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogEditEvent
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogEditViewModel
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogViewModel
import de.familyplanner.android.feature.tasks.ui.history.HistoryArgs
import de.familyplanner.android.feature.tasks.ui.history.HistoryViewModel
import org.junit.Rule
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeTaskRepository()
    private val catalog = FakeCatalogRepository()
    private val sessions = InMemorySessionStore(parentSession)
    private val tasks = (1..21).map { CatalogTask("k-$it", "Aufgabe $it", null, 1) }

    private fun assignVm(args: AssignArgs = AssignArgs("c-1")): AssignViewModel {
        catalog.listResult = ApiResult.Success(tasks)
        return AssignViewModel(
            args,
            LoadBoardUseCase(repository, InMemoryBoardCache(), sessions),
            LoadCatalogUseCase(catalog),
            AssignTasksUseCase(repository),
        )
    }

    @Test
    fun `REQ-TASK-020 assign view model preselects child and limits to 20`() {
        val vm = assignVm()
        assertEquals("c-1", vm.state.value.memberId)
        assertEquals(
            listOf("Ben", "Lea"),
            vm.state.value.children
                .map { it.name },
        )

        tasks.forEach { vm.onEvent(AssignEvent.Toggle(it.id)) }
        assertEquals(21, vm.state.value.selected.size)
        assertTrue(vm.state.value.tooMany)
        assertFalse(vm.state.value.canSubmit)

        vm.onEvent(AssignEvent.Toggle("k-21"))
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `REQ-TASK-020 assign submits the selection for the chosen child and finishes`() {
        val vm = assignVm(AssignArgs())
        assertEquals(null, vm.state.value.memberId)
        vm.onEvent(AssignEvent.SelectChild("c-2"))
        vm.onEvent(AssignEvent.Toggle("k-3"))

        vm.onEvent(AssignEvent.Submit)

        assertEquals(listOf("c-2" to listOf("k-3")), repository.assigned)
        assertTrue(vm.state.value.isDone)
    }

    @Test
    fun `REQ-TASK-020 assign shows the error and stays open on failure`() {
        repository.assignResult = ApiResult.Failure(AppError.Forbidden)
        val vm = assignVm()
        vm.onEvent(AssignEvent.Toggle("k-1"))

        vm.onEvent(AssignEvent.Submit)

        assertEquals(AppError.Forbidden, vm.state.value.submitError)
        assertFalse(vm.state.value.isDone)
    }

    @Test
    fun `REQ-TASK-020 a single child is preselected and an empty catalog is detected`() {
        repository.boardResult = ApiResult.Success(listOf(leaLane))
        catalog.listResult = ApiResult.Success(emptyList())
        val vm =
            AssignViewModel(
                AssignArgs(),
                LoadBoardUseCase(repository, InMemoryBoardCache(), sessions),
                LoadCatalogUseCase(catalog),
                AssignTasksUseCase(repository),
            )

        assertEquals("c-2", vm.state.value.memberId)
        assertTrue(vm.state.value.catalogEmpty)
    }

    @Test
    fun `REQ-TASK-020 assign reports a load error`() {
        repository.boardResult = ApiResult.Failure(AppError.Server)
        val vm = assignVm()

        assertEquals(AppError.Server, vm.state.value.error)
    }

    private fun catalogUseCases() =
        CatalogUseCases(LoadCatalogUseCase(catalog), SaveCatalogTaskUseCase(catalog), ArchiveCatalogTaskUseCase(catalog))

    @Test
    fun `REQ-TASK-010 catalog list loads the active tasks`() {
        catalog.listResult = ApiResult.Success(listOf(CatalogTask("k-1", "Müll", "🗑", 1)))

        val vm = CatalogViewModel(LoadCatalogUseCase(catalog))

        assertEquals(
            listOf("k-1"),
            vm.state.value.tasks
                .map { it.id },
        )
        catalog.listResult = ApiResult.Failure(AppError.Network)
        vm.refresh()
        assertEquals(AppError.Network, vm.state.value.error)
    }

    @Test
    fun `REQ-TASK-010 catalog edit saves a new task`() {
        val vm = CatalogEditViewModel(CatalogEditArgs(), catalogUseCases())
        vm.onEvent(CatalogEditEvent.TitleChanged("Bad putzen"))
        vm.onEvent(CatalogEditEvent.EmojiChanged("🛁"))
        vm.onEvent(CatalogEditEvent.PointsChanged(500))

        vm.onEvent(CatalogEditEvent.Save)

        assertEquals(100, vm.state.value.points)
        assertEquals("Bad putzen", catalog.created.single().title)
        assertTrue(vm.state.value.isDone)
    }

    @Test
    fun `REQ-TASK-010 catalog edit loads, updates and archives with confirmation`() {
        catalog.listResult = ApiResult.Success(listOf(CatalogTask("k-1", "Müll", "🗑", 2)))
        val vm = CatalogEditViewModel(CatalogEditArgs("k-1"), catalogUseCases())
        assertEquals("Müll", vm.state.value.title)
        assertEquals(2, vm.state.value.points)

        vm.onEvent(CatalogEditEvent.TitleChanged("Müll rausbringen"))
        vm.onEvent(CatalogEditEvent.Save)
        assertEquals("k-1", catalog.updated.single().first)

        val second = CatalogEditViewModel(CatalogEditArgs("k-1"), catalogUseCases())
        second.onEvent(CatalogEditEvent.AskArchive)
        assertTrue(second.state.value.confirmingArchive)
        assertTrue(catalog.archived.isEmpty())
        second.onEvent(CatalogEditEvent.ConfirmArchive)
        assertEquals(listOf("k-1"), catalog.archived)
        assertTrue(second.state.value.isDone)
    }

    @Test
    fun `REQ-TASK-010 catalog edit reports an unknown or archived task`() {
        catalog.listResult = ApiResult.Success(emptyList())

        val vm = CatalogEditViewModel(CatalogEditArgs("k-9"), catalogUseCases())

        assertEquals(AppError.NotFound, vm.state.value.loadError)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `REQ-TASK-010 catalog edit keeps the validation message`() {
        val vm = CatalogEditViewModel(CatalogEditArgs(), catalogUseCases())
        vm.onEvent(CatalogEditEvent.TitleChanged("A"))
        catalog.saveResult = ApiResult.Failure(AppError.Validation("Punkte müssen zwischen 1 und 100 liegen"))

        vm.onEvent(CatalogEditEvent.Save)

        assertEquals(AppError.Validation("Punkte müssen zwischen 1 und 100 liegen"), vm.state.value.error)
        assertFalse(vm.state.value.isDone)
    }

    @Test
    fun `REQ-TASK-025 history lists done cards newest first`() {
        val older = card("o", status = TaskStatus.DONE, doneAt = Instant.parse("2026-10-20T10:00:00Z"))
        val newer = card("n", status = TaskStatus.DONE, doneAt = Instant.parse("2026-10-23T10:00:00Z"))
        repository.historyResult = ApiResult.Success(listOf(older, newer))

        val vm = HistoryViewModel(HistoryArgs("c-1", "Ben"), LoadHistoryUseCase(repository))

        assertEquals(
            listOf("n", "o"),
            vm.state.value.cards
                .map { it.id },
        )
        repository.historyResult = ApiResult.Success(emptyList())
        vm.refresh()
        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun `REQ-TASK-025 history shows a load error`() {
        repository.historyResult = ApiResult.Failure(AppError.Network)

        val vm = HistoryViewModel(HistoryArgs("c-1", "Ben"), LoadHistoryUseCase(repository))

        assertEquals(AppError.Network, vm.state.value.error)
        assertFalse(vm.state.value.isEmpty)
        assertEquals(benLane.name, "Ben")
    }
}
