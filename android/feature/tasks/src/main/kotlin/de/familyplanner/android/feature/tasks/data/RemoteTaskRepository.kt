package de.familyplanner.android.feature.tasks.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.core.data.apiCallNoContent
import de.familyplanner.android.feature.tasks.domain.board.TaskCard
import de.familyplanner.android.feature.tasks.domain.board.TaskLane
import de.familyplanner.android.feature.tasks.domain.board.TaskRepository
import de.familyplanner.api.client.TasksApi
import de.familyplanner.api.model.AssignTasksRequest
import de.familyplanner.api.model.RejectTaskRequest
import kotlinx.serialization.json.Json

class RemoteTaskRepository(
    private val tasks: TasksApi,
    private val json: Json,
) : TaskRepository {
    override suspend fun board(): ApiResult<List<TaskLane>> =
        apiCall(json, { tasks.getTaskBoard() }) { response -> response.data?.lanes?.mapAllOrNull { it.toDomain() } }

    override suspend fun history(
        memberId: String,
        limit: Int,
    ): ApiResult<List<TaskCard>> = apiCall(json, { tasks.getTaskHistory(memberId, limit) }) { response -> response.data?.cards() }

    override suspend fun assign(
        memberId: String,
        catalogTaskIds: List<String>,
    ): ApiResult<List<TaskCard>> =
        apiCall(json, { tasks.assignTasks(AssignTasksRequest(memberId, catalogTaskIds)) }) { response -> response.data?.cards() }

    override suspend fun report(cardId: String): ApiResult<TaskCard> = apiCall(json, { tasks.reportTask(cardId) }) { it.data?.toDomain() }

    override suspend fun withdraw(cardId: String): ApiResult<TaskCard> =
        apiCall(json, { tasks.withdrawTaskReport(cardId) }) { it.data?.toDomain() }

    override suspend fun accept(cardId: String): ApiResult<TaskCard> = apiCall(json, { tasks.acceptTask(cardId) }) { it.data?.toDomain() }

    /** Immer mit Body senden: Retrofit lehnt einen `null`-Body ab; `{}` heißt „kein Grund“. */
    override suspend fun reject(
        cardId: String,
        reason: String?,
    ): ApiResult<TaskCard> = apiCall(json, { tasks.rejectTask(cardId, RejectTaskRequest(reason)) }) { it.data?.toDomain() }

    override suspend fun remove(cardId: String): ApiResult<Unit> = apiCallNoContent(json) { tasks.removeTaskCard(cardId) }

    private fun List<de.familyplanner.api.model.TaskCard>.cards(): List<TaskCard>? = mapAllOrNull { it.toDomain() }
}
