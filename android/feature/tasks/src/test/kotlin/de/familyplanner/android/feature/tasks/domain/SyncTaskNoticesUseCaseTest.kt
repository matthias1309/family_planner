package de.familyplanner.android.feature.tasks.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.domain.notice.SyncTaskNoticesUseCase
import de.familyplanner.android.feature.tasks.domain.notice.TaskNotice
import de.familyplanner.android.feature.tasks.domain.notice.TaskSyncOutcome
import de.familyplanner.android.feature.tasks.fakes.FakeTaskNotifier
import de.familyplanner.android.feature.tasks.fakes.FakeTaskRepository
import de.familyplanner.android.feature.tasks.fakes.InMemoryBoardCache
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.lane
import de.familyplanner.android.feature.tasks.fakes.networkDown
import de.familyplanner.android.feature.tasks.fakes.noon
import de.familyplanner.android.feature.tasks.fakes.parentSession
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SyncTaskNoticesUseCaseTest {
    private val repository = FakeTaskRepository()
    private val cache = InMemoryBoardCache()
    private val notifier = FakeTaskNotifier()
    private val sessions = InMemorySessionStore(parentSession)

    private fun sync() = SyncTaskNoticesUseCase(sessions, repository, cache, notifier, Clock.fixed(noon, ZoneOffset.UTC))

    @Test
    fun `REQ-TASK-030 first sync stores a baseline without notices`() =
        runTest {
            repository.boardResult = ApiResult.Success(listOf(lane("c-1", "Ben", card("a", status = TaskStatus.REVIEW))))

            val outcome = sync()()

            assertEquals(TaskSyncOutcome.SYNCED, outcome)
            assertEquals(emptyList(), notifier.notices)
            assertNotNull(cache.read("p-1"))
        }

    @Test
    fun `REQ-TASK-030 parents are told once about a new report`() =
        runTest {
            cache.seed("p-1", lane("c-1", "Ben", card("a", title = "Bad")))
            repository.boardResult = ApiResult.Success(listOf(lane("c-1", "Ben", card("a", title = "Bad", status = TaskStatus.REVIEW))))
            val useCase = sync()

            useCase()
            useCase()

            assertEquals(listOf<TaskNotice>(TaskNotice.Reported("a", "Bad", "Ben")), notifier.notices)
        }

    @Test
    fun `REQ-TASK-030 sync keeps the board when the server is unreachable`() =
        runTest {
            cache.seed("p-1", lane("c-1", "Ben", card("a")))
            repository.boardResult = networkDown

            val outcome = sync()()

            assertEquals(TaskSyncOutcome.UNREACHABLE, outcome)
            assertNotNull(cache.read("p-1"))
            assertEquals(0, cache.cleared)
        }

    @Test
    fun `REQ-TASK-030 unauthorized sync clears the local board`() =
        runTest {
            cache.seed("p-1", lane("c-1", "Ben", card("a")))
            repository.boardResult = ApiResult.Failure(AppError.Unauthorized)

            val outcome = sync()()

            assertEquals(TaskSyncOutcome.SIGNED_OUT, outcome)
            assertEquals(1, cache.cleared)
            assertNull(cache.read("p-1"))
        }

    @Test
    fun `REQ-TASK-030 signed out device clears the local board`() =
        runTest {
            sessions.setCurrent(null)

            assertEquals(TaskSyncOutcome.SIGNED_OUT, sync()())
            assertEquals(1, cache.cleared)
        }
}
