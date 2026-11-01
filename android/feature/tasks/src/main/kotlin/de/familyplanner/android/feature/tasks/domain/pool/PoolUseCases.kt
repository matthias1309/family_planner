package de.familyplanner.android.feature.tasks.domain.pool

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.tasks.domain.board.BoardCache
import de.familyplanner.android.feature.tasks.domain.board.CatalogRepository
import de.familyplanner.android.feature.tasks.domain.board.TaskCard
import de.familyplanner.android.feature.tasks.domain.board.TaskRepository
import de.familyplanner.android.feature.tasks.domain.board.adding
import de.familyplanner.android.feature.tasks.domain.board.updateCache
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask

class LoadPoolUseCase(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(): ApiResult<List<CatalogTask>> = repository.pool()
}

/** Karte aus dem Pool (REQ-TASK-028); der lokale Stand bekommt sie sofort, damit der Abgleich keinen „Neue Aufgabe“-Hinweis zeigt. */
class TakePoolTaskUseCase(
    private val repository: TaskRepository,
    private val cache: BoardCache,
    private val sessions: SessionStore,
) {
    suspend operator fun invoke(
        catalogTaskId: String,
        alreadyDone: Boolean,
    ): ApiResult<TaskCard> =
        repository.takeFromPool(catalogTaskId, alreadyDone).also { result ->
            if (result is ApiResult.Success) updateCache(sessions, cache) { it.adding(result.data) }
        }
}
