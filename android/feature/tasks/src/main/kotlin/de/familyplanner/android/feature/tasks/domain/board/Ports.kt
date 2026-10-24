package de.familyplanner.android.feature.tasks.domain.board

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogInput
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import de.familyplanner.android.feature.tasks.domain.notice.TaskNotice
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun board(): ApiResult<List<TaskLane>>

    suspend fun history(
        memberId: String,
        limit: Int,
    ): ApiResult<List<TaskCard>>

    suspend fun assign(
        memberId: String,
        catalogTaskIds: List<String>,
    ): ApiResult<List<TaskCard>>

    suspend fun report(cardId: String): ApiResult<TaskCard>

    suspend fun withdraw(cardId: String): ApiResult<TaskCard>

    suspend fun accept(cardId: String): ApiResult<TaskCard>

    suspend fun reject(
        cardId: String,
        reason: String?,
    ): ApiResult<TaskCard>

    suspend fun remove(cardId: String): ApiResult<Unit>
}

interface CatalogRepository {
    suspend fun list(): ApiResult<List<CatalogTask>>

    suspend fun create(input: CatalogInput): ApiResult<CatalogTask>

    suspend fun update(
        id: String,
        input: CatalogInput,
    ): ApiResult<CatalogTask>

    suspend fun archive(id: String): ApiResult<Unit>
}

/** Lokaler Lese-Stand des Boards (ADR-0011). Fehler beim Speichern sind nie ein Nutzerfehler. */
interface BoardCache {
    /** Gespeicherter Stand für dieses Mitglied, sonst `null`. */
    suspend fun read(memberId: String): BoardSnapshot?

    suspend fun write(
        memberId: String,
        board: BoardSnapshot,
    )

    /** Ändert den Stand dieses Mitglieds atomar; ohne passenden Stand geschieht nichts. */
    suspend fun update(
        memberId: String,
        change: (BoardSnapshot) -> BoardSnapshot,
    )

    suspend fun clear()

    /** Für das Badge: letzter Stand samt Besitzer, `null` ohne Stand. */
    fun observe(): Flow<CachedBoard?>
}

interface TaskNotifier {
    fun announce(notice: TaskNotice)
}

/** Stößt den Abgleich im Hintergrund an (WorkManager, ADR-0013). */
interface TaskSync {
    fun requestNow()

    fun schedulePeriodic()
}
