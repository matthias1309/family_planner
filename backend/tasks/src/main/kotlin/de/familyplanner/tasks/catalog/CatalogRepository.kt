package de.familyplanner.tasks.catalog

import java.time.Instant

interface CatalogRepository {
    /** Sorted by title, case-insensitive. */
    suspend fun findAll(includeArchived: Boolean): List<CatalogTask>

    suspend fun findById(id: CatalogTaskId): CatalogTask?

    /** Returns only the tasks among [ids] that exist and are not archived. */
    suspend fun findActiveByIds(ids: Collection<CatalogTaskId>): List<CatalogTask>

    suspend fun create(
        task: CatalogTask,
        at: Instant,
    )

    /** Returns `false` if the task is unknown or archived. */
    suspend fun update(
        task: CatalogTask,
        at: Instant,
    ): Boolean

    /** Archives the task; archiving twice is fine. Returns `false` only if the task is unknown. */
    suspend fun archive(
        id: CatalogTaskId,
        at: Instant,
    ): Boolean
}
