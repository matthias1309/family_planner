package de.familyplanner.android.feature.tasks.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.tasks.domain.board.AssignTasksUseCase
import de.familyplanner.android.feature.tasks.domain.board.BoardLoad
import de.familyplanner.android.feature.tasks.domain.board.CardAction
import de.familyplanner.android.feature.tasks.domain.board.LoadBoardUseCase
import de.familyplanner.android.feature.tasks.domain.board.MoveCardUseCase
import de.familyplanner.android.feature.tasks.domain.board.RemoveCardUseCase
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.domain.catalog.ArchiveCatalogTaskUseCase
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogInput
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import de.familyplanner.android.feature.tasks.domain.catalog.LoadCatalogUseCase
import de.familyplanner.android.feature.tasks.domain.catalog.SaveCatalogTaskUseCase
import de.familyplanner.android.feature.tasks.fakes.FakeCatalogRepository
import de.familyplanner.android.feature.tasks.fakes.FakeTaskRepository
import de.familyplanner.android.feature.tasks.fakes.InMemoryBoardCache
import de.familyplanner.android.feature.tasks.fakes.benLane
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.childSession
import de.familyplanner.android.feature.tasks.fakes.lane
import de.familyplanner.android.feature.tasks.fakes.networkDown
import de.familyplanner.android.feature.tasks.fakes.noon
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UseCasesTest {
    private val repository = FakeTaskRepository()
    private val cache = InMemoryBoardCache()
    private val sessions = InMemorySessionStore(childSession)
    private val clock = Clock.fixed(noon, ZoneOffset.UTC)

    private fun load() = LoadBoardUseCase(repository, cache, sessions, clock)

    @Test
    fun `REQ-TASK-021 fresh board is stored for the member`() =
        runTest {
            repository.boardResult = ApiResult.Success(listOf(lane("c-1", "Ben", card("a"))))

            val result = load()()

            assertIs<BoardLoad.Fresh>(result)
            assertEquals(
                listOf("a"),
                cache
                    .read("c-1")
                    ?.lanes
                    ?.single()
                    ?.cards
                    ?.map { it.id },
            )
            assertEquals(noon, cache.read("c-1")?.syncedAt)
        }

    @Test
    fun `REQ-TASK-021 board falls back to the cached board when offline`() =
        runTest {
            cache.seed("c-1", benLane)
            repository.boardResult = networkDown

            val result = load()()

            assertIs<BoardLoad.Cached>(result)
            assertEquals(AppError.Network, result.error)
        }

    @Test
    fun `REQ-TASK-021 cached board of another member is not used`() =
        runTest {
            cache.seed("p-1", benLane)
            repository.boardResult = networkDown

            assertIs<BoardLoad.Failed>(load()())
        }

    @Test
    fun `REQ-TASK-021 other errors do not fall back to the cache`() =
        runTest {
            cache.seed("c-1", benLane)
            repository.boardResult = ApiResult.Failure(AppError.Unauthorized)

            assertEquals(BoardLoad.Failed(AppError.Unauthorized), load()())
        }

    @Test
    fun `REQ-TASK-022 reporting updates the local board`() =
        runTest {
            cache.seed("c-1", lane("c-1", "Ben", card("a")))

            val result = MoveCardUseCase(repository, cache, sessions)("a", CardAction.REPORT)

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(
                TaskStatus.REVIEW,
                cache
                    .read("c-1")
                    ?.lanes
                    ?.single()
                    ?.cards
                    ?.single()
                    ?.status,
            )
            assertEquals(listOf("report" to "a"), repository.moves)
        }

    @Test
    fun `REQ-TASK-023 reject sends a trimmed reason or none`() =
        runTest {
            val move = MoveCardUseCase(repository, cache, sessions)

            move("a", CardAction.REJECT, "   ")
            move("b", CardAction.REJECT, " ok ")

            assertEquals(listOf<Pair<String, String?>>("a" to null, "b" to "ok"), repository.rejections)
        }

    @Test
    fun `REQ-TASK-023 reject refuses a reason that is too long`() =
        runTest {
            val result = MoveCardUseCase(repository, cache, sessions)("a", CardAction.REJECT, "x".repeat(201))

            assertIs<ApiResult.Failure>(result)
            assertIs<AppError.Validation>(result.error)
            assertTrue(repository.rejections.isEmpty())
        }

    @Test
    fun `REQ-TASK-023 accepting directly uses the accept call`() =
        runTest {
            MoveCardUseCase(repository, cache, sessions)("a", CardAction.ACCEPT_DIRECTLY)

            assertEquals(listOf("accept" to "a"), repository.moves)
        }

    @Test
    fun `REQ-TASK-023 move does not handle removal`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { MoveCardUseCase(repository, cache, sessions)("a", CardAction.REMOVE) }
        }

    @Test
    fun `REQ-TASK-022 a failed move leaves the local board alone`() =
        runTest {
            cache.seed("c-1", lane("c-1", "Ben", card("a")))
            repository.moveResult = { ApiResult.Failure(AppError.Conflict("schon erledigt")) }

            MoveCardUseCase(repository, cache, sessions)("a", CardAction.REPORT)

            assertEquals(
                TaskStatus.TODO,
                cache
                    .read("c-1")
                    ?.lanes
                    ?.single()
                    ?.cards
                    ?.single()
                    ?.status,
            )
        }

    @Test
    fun `REQ-TASK-024 removing deletes the card from the local board`() =
        runTest {
            cache.seed("c-1", lane("c-1", "Ben", card("a"), card("b")))

            RemoveCardUseCase(repository, cache, sessions)("a")

            assertEquals(
                listOf("b"),
                cache
                    .read("c-1")
                    ?.lanes
                    ?.single()
                    ?.cards
                    ?.map { it.id },
            )
        }

    @Test
    fun `REQ-TASK-020 assign needs a child and 1 to 20 tasks`() =
        runTest {
            val assign = AssignTasksUseCase(repository)

            assertIs<ApiResult.Failure>(assign(null, listOf("k-1")))
            assertIs<ApiResult.Failure>(assign("c-1", emptyList()))
            assertIs<ApiResult.Failure>(assign("c-1", (1..21).map { "k-$it" }))
            assertTrue(repository.assigned.isEmpty())

            assertIs<ApiResult.Success<*>>(assign("c-1", (1..20).map { "k-$it" }))
            assertEquals(1, repository.assigned.size)
        }

    @Test
    fun `REQ-TASK-010 catalog save validates and trims before sending`() =
        runTest {
            val catalog = FakeCatalogRepository()
            val save = SaveCatalogTaskUseCase(catalog)

            assertIs<ApiResult.Failure>(save(null, CatalogInput(" ", null, 1)))
            assertTrue(catalog.created.isEmpty())

            save(null, CatalogInput("  Bad  ", "  ", 3))
            save("k-1", CatalogInput("Müll", " 🗑 ", 2))

            assertEquals(listOf(CatalogInput("Bad", null, 3)), catalog.created)
            assertEquals(listOf("k-1" to CatalogInput("Müll", "🗑", 2)), catalog.updated)
        }

    @Test
    fun `REQ-TASK-010 catalog loads and archives`() =
        runTest {
            val catalog = FakeCatalogRepository()
            catalog.listResult = ApiResult.Success(listOf(CatalogTask("k-1", "Müll", null, 1)))

            assertEquals(1, (LoadCatalogUseCase(catalog)() as ApiResult.Success).data.size)
            ArchiveCatalogTaskUseCase(catalog)("k-1")

            assertEquals(listOf("k-1"), catalog.archived)
        }
}
