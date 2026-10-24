package de.familyplanner.android.feature.tasks.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.core.data.apiCallNoContent
import de.familyplanner.android.feature.tasks.domain.board.CatalogRepository
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogInput
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import de.familyplanner.api.client.TasksApi
import kotlinx.serialization.json.Json

class RemoteCatalogRepository(
    private val tasks: TasksApi,
    private val json: Json,
) : CatalogRepository {
    override suspend fun list(): ApiResult<List<CatalogTask>> =
        apiCall(json, { tasks.listCatalogTasks() }) { response -> response.data?.filterNot { it.archived }?.map { it.toDomain() } }

    override suspend fun create(input: CatalogInput): ApiResult<CatalogTask> =
        apiCall(json, { tasks.createCatalogTask(input.toApi()) }) { it.data?.toDomain() }

    override suspend fun update(
        id: String,
        input: CatalogInput,
    ): ApiResult<CatalogTask> = apiCall(json, { tasks.updateCatalogTask(id, input.toApi()) }) { it.data?.toDomain() }

    override suspend fun archive(id: String): ApiResult<Unit> = apiCallNoContent(json) { tasks.archiveCatalogTask(id) }
}
