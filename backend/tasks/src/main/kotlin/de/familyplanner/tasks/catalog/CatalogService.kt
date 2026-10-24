package de.familyplanner.tasks.catalog

import de.familyplanner.core.error.AppException
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

private const val TASK_NOT_FOUND = "Aufgabe nicht gefunden"

class CatalogService(
    private val catalog: CatalogRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(CatalogService::class.java)

    suspend fun list(includeArchived: Boolean): List<CatalogTask> = catalog.findAll(includeArchived)

    suspend fun create(input: CatalogTaskInput): CatalogTask {
        val task = toTask(CatalogTaskId(newId()), validated(input))
        catalog.create(task, clock.instant())
        log.info("Catalog task created: {}", task.id.value)
        return task
    }

    suspend fun update(
        id: CatalogTaskId,
        input: CatalogTaskInput,
    ): CatalogTask {
        val existing = catalog.findById(id) ?: throw AppException.NotFound(TASK_NOT_FOUND)
        val task = toTask(id, validated(input))
        if (existing.archived || !catalog.update(task, clock.instant())) throw AppException.Conflict(ARCHIVED)
        log.info("Catalog task updated: {}", id.value)
        return task
    }

    suspend fun archive(id: CatalogTaskId) {
        if (!catalog.archive(id, clock.instant())) throw AppException.NotFound(TASK_NOT_FOUND)
        log.info("Catalog task archived: {}", id.value)
    }

    private fun toTask(
        id: CatalogTaskId,
        input: CatalogTaskInput,
    ) = CatalogTask(id, input.title, input.emoji, input.points, archived = false)

    private companion object {
        const val ARCHIVED = "Die Aufgabe ist archiviert"
    }
}
