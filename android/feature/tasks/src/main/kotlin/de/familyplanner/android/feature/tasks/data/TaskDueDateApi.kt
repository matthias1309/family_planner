package de.familyplanner.android.feature.tasks.data

import de.familyplanner.api.model.TaskCardResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PUT
import retrofit2.http.Path
import java.time.Instant

/**
 * Eigener Endpunkt, weil `explicitNulls = false` ein `dueAt: null` der generierten Klasse wegließe –
 * der Server verlangt den Schlüssel (`{}` ergibt 400).
 */
interface TaskDueDateApi {
    @PUT("api/v1/tasks/cards/{cardId}/due-date")
    suspend fun setTaskDueDate(
        @Path("cardId") cardId: String,
        @Body body: JsonObject,
    ): Response<TaskCardResponse>
}

internal fun dueDateBody(dueAt: Instant?): JsonObject = buildJsonObject { put("dueAt", dueAt?.toString()) }
