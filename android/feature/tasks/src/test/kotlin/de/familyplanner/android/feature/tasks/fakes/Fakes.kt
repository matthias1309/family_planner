package de.familyplanner.android.feature.tasks.fakes

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.feature.tasks.domain.board.BoardCache
import de.familyplanner.android.feature.tasks.domain.board.BoardSnapshot
import de.familyplanner.android.feature.tasks.domain.board.CachedBoard
import de.familyplanner.android.feature.tasks.domain.board.CatalogRepository
import de.familyplanner.android.feature.tasks.domain.board.TaskCard
import de.familyplanner.android.feature.tasks.domain.board.TaskLane
import de.familyplanner.android.feature.tasks.domain.board.TaskNotifier
import de.familyplanner.android.feature.tasks.domain.board.TaskRepository
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.domain.board.TaskSync
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogInput
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import de.familyplanner.android.feature.tasks.domain.notice.TaskNotice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

val parentSession = Session("tok", "p-1", "Anna", Role.PARENT, "d-1")
val childSession = Session("tok", "c-1", "Ben", Role.CHILD, "d-2")

val noon: Instant = Instant.parse("2026-10-24T10:00:00Z")

@Suppress("LongParameterList")
fun card(
    id: String,
    memberId: String = "c-1",
    title: String = "Aufgabe $id",
    status: TaskStatus = TaskStatus.TODO,
    points: Int = 2,
    reason: String? = null,
    assignedAt: Instant = noon,
    reportedAt: Instant? = null,
    doneAt: Instant? = null,
    emoji: String? = null,
) = TaskCard(id, memberId, title, emoji, points, status, reason, assignedAt, reportedAt, doneAt)

fun lane(
    memberId: String,
    name: String,
    vararg cards: TaskCard,
) = TaskLane(memberId, name, cards.toList())

val benLane = lane("c-1", "Ben")
val leaLane = lane("c-2", "Lea")

class FakeTaskRepository : TaskRepository {
    var boardResult: ApiResult<List<TaskLane>> = ApiResult.Success(listOf(benLane, leaLane))
    var historyResult: ApiResult<List<TaskCard>> = ApiResult.Success(emptyList())
    var assignResult: ApiResult<List<TaskCard>> = ApiResult.Success(emptyList())
    var moveResult: ((String) -> ApiResult<TaskCard>)? = null
    var removeResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var boardCalls = 0
    val assigned = mutableListOf<Pair<String, List<String>>>()
    val moves = mutableListOf<Pair<String, String>>()
    val rejections = mutableListOf<Pair<String, String?>>()
    val removed = mutableListOf<String>()

    override suspend fun board(): ApiResult<List<TaskLane>> {
        boardCalls++
        return boardResult
    }

    override suspend fun history(
        memberId: String,
        limit: Int,
    ) = historyResult

    override suspend fun assign(
        memberId: String,
        catalogTaskIds: List<String>,
    ): ApiResult<List<TaskCard>> {
        assigned += memberId to catalogTaskIds
        return assignResult
    }

    private fun move(
        name: String,
        cardId: String,
        status: TaskStatus,
    ): ApiResult<TaskCard> {
        moves += name to cardId
        return moveResult?.invoke(cardId) ?: ApiResult.Success(card(cardId, status = status))
    }

    override suspend fun report(cardId: String) = move("report", cardId, TaskStatus.REVIEW)

    override suspend fun withdraw(cardId: String) = move("withdraw", cardId, TaskStatus.TODO)

    override suspend fun accept(cardId: String) = move("accept", cardId, TaskStatus.DONE)

    override suspend fun reject(
        cardId: String,
        reason: String?,
    ): ApiResult<TaskCard> {
        rejections += cardId to reason
        return move("reject", cardId, TaskStatus.TODO)
    }

    override suspend fun remove(cardId: String): ApiResult<Unit> {
        removed += cardId
        return removeResult
    }
}

class FakeCatalogRepository : CatalogRepository {
    var listResult: ApiResult<List<CatalogTask>> = ApiResult.Success(emptyList())
    var saveResult: ApiResult<CatalogTask>? = null
    var archiveResult: ApiResult<Unit> = ApiResult.Success(Unit)

    val created = mutableListOf<CatalogInput>()
    val updated = mutableListOf<Pair<String, CatalogInput>>()
    val archived = mutableListOf<String>()

    override suspend fun list() = listResult

    override suspend fun create(input: CatalogInput): ApiResult<CatalogTask> {
        created += input
        return saveResult ?: ApiResult.Success(CatalogTask("k-new", input.title, input.emoji, input.points))
    }

    override suspend fun update(
        id: String,
        input: CatalogInput,
    ): ApiResult<CatalogTask> {
        updated += id to input
        return saveResult ?: ApiResult.Success(CatalogTask(id, input.title, input.emoji, input.points))
    }

    override suspend fun archive(id: String): ApiResult<Unit> {
        archived += id
        return archiveResult
    }
}

class InMemoryBoardCache : BoardCache {
    private val stored = MutableStateFlow<CachedBoard?>(null)
    var cleared = 0
    var writes = 0

    override suspend fun read(memberId: String) = stored.value?.takeIf { it.memberId == memberId }?.board

    override suspend fun write(
        memberId: String,
        board: BoardSnapshot,
    ) {
        writes++
        stored.value = CachedBoard(memberId, board)
    }

    override suspend fun update(
        memberId: String,
        change: (BoardSnapshot) -> BoardSnapshot,
    ) {
        stored.value = stored.value?.takeIf { it.memberId == memberId }?.let { it.copy(board = change(it.board)) } ?: stored.value
    }

    override suspend fun clear() {
        cleared++
        stored.value = null
    }

    override fun observe(): Flow<CachedBoard?> = stored

    fun seed(
        memberId: String,
        vararg lanes: TaskLane,
    ) {
        stored.value = CachedBoard(memberId, BoardSnapshot(lanes.toList(), noon))
    }
}

class FakeTaskNotifier : TaskNotifier {
    val notices = mutableListOf<TaskNotice>()

    override fun announce(notice: TaskNotice) {
        notices += notice
    }
}

class FakeTaskSync : TaskSync {
    var requests = 0
    var periodic = 0

    override fun requestNow() {
        requests++
    }

    override fun schedulePeriodic() {
        periodic++
    }
}

val networkDown = ApiResult.Failure(AppError.Network)
