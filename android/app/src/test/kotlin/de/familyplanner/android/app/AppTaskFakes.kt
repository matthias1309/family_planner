package de.familyplanner.android.app

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.feature.tasks.domain.board.BoardCache
import de.familyplanner.android.feature.tasks.domain.board.BoardSnapshot
import de.familyplanner.android.feature.tasks.domain.board.CachedBoard
import de.familyplanner.android.feature.tasks.domain.board.TaskCard
import de.familyplanner.android.feature.tasks.domain.board.TaskLane
import de.familyplanner.android.feature.tasks.domain.board.TaskRepository
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

private val assignedAt = Instant.parse("2026-10-24T10:00:00Z")

fun taskCard(
    id: String,
    memberId: String,
    title: String,
    status: TaskStatus = TaskStatus.TODO,
) = TaskCard(id, memberId, title, null, 2, status, null, assignedAt, assignedAt.takeIf { status == TaskStatus.REVIEW }, null)

/** Board mit fester Antwort; alle Aktionen sind im Navigationstest nicht gefragt. */
class StaticTaskRepository(
    private val lanes: List<TaskLane>,
) : TaskRepository {
    private val unsupported = ApiResult.Failure(de.familyplanner.android.core.data.AppError.Network)

    override suspend fun board(): ApiResult<List<TaskLane>> = ApiResult.Success(lanes)

    override suspend fun history(
        memberId: String,
        limit: Int,
    ): ApiResult<List<TaskCard>> = ApiResult.Success(emptyList())

    override suspend fun assign(
        memberId: String,
        catalogTaskIds: List<String>,
    ): ApiResult<List<TaskCard>> = unsupported

    override suspend fun report(cardId: String): ApiResult<TaskCard> = unsupported

    override suspend fun withdraw(cardId: String): ApiResult<TaskCard> = unsupported

    override suspend fun accept(cardId: String): ApiResult<TaskCard> = unsupported

    override suspend fun reject(
        cardId: String,
        reason: String?,
    ): ApiResult<TaskCard> = unsupported

    override suspend fun remove(cardId: String): ApiResult<Unit> = unsupported
}

class MemoryBoardCache : BoardCache {
    private val stored = MutableStateFlow<CachedBoard?>(null)

    override suspend fun read(memberId: String) = stored.value?.takeIf { it.memberId == memberId }?.board

    override suspend fun write(
        memberId: String,
        board: BoardSnapshot,
    ) {
        stored.value = CachedBoard(memberId, board)
    }

    override suspend fun update(
        memberId: String,
        change: (BoardSnapshot) -> BoardSnapshot,
    ) {
        stored.value = stored.value?.takeIf { it.memberId == memberId }?.let { it.copy(board = change(it.board)) } ?: stored.value
    }

    override suspend fun clear() {
        stored.value = null
    }

    override fun observe(): Flow<CachedBoard?> = stored
}
