package de.familyplanner.tasks.persistence

import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.tasks.catalog.CatalogRepository
import de.familyplanner.tasks.catalog.CatalogTask
import de.familyplanner.tasks.catalog.CatalogTaskId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

class ExposedCatalogRepository(
    private val db: Database,
) : CatalogRepository {
    override suspend fun findAll(includeArchived: Boolean): List<CatalogTask> =
        dbQuery(db) {
            val rows = TaskCatalogTable.selectAll()
            val visible = if (includeArchived) rows else rows.where { TaskCatalogTable.archivedAt.isNull() }
            visible.map { it.toTask() }.sortedBy { it.title.lowercase() }
        }

    override suspend fun findById(id: CatalogTaskId): CatalogTask? =
        dbQuery(db) {
            TaskCatalogTable
                .selectAll()
                .where { TaskCatalogTable.id eq id.value }
                .firstOrNull()
                ?.toTask()
        }

    override suspend fun findActiveByIds(ids: Collection<CatalogTaskId>): List<CatalogTask> =
        dbQuery(db) {
            TaskCatalogTable
                .selectAll()
                .where { (TaskCatalogTable.id inList ids.map { it.value }) and TaskCatalogTable.archivedAt.isNull() }
                .map { it.toTask() }
        }

    override suspend fun create(
        task: CatalogTask,
        at: Instant,
    ) {
        dbQuery(db) {
            TaskCatalogTable.insert {
                it[id] = task.id.value
                it[title] = task.title
                it[emoji] = task.emoji
                it[points] = task.points
                it[createdAt] = at.toEpochMilli()
                it[updatedAt] = at.toEpochMilli()
            }
        }
    }

    override suspend fun update(
        task: CatalogTask,
        at: Instant,
    ): Boolean =
        dbQuery(db) {
            val changed =
                TaskCatalogTable.update({ (TaskCatalogTable.id eq task.id.value) and TaskCatalogTable.archivedAt.isNull() }) {
                    it[title] = task.title
                    it[emoji] = task.emoji
                    it[points] = task.points
                    it[updatedAt] = at.toEpochMilli()
                }
            changed > 0
        }

    override suspend fun archive(
        id: CatalogTaskId,
        at: Instant,
    ): Boolean =
        dbQuery(db) {
            TaskCatalogTable.update({ (TaskCatalogTable.id eq id.value) and TaskCatalogTable.archivedAt.isNull() }) {
                it[archivedAt] = at.toEpochMilli()
                it[updatedAt] = at.toEpochMilli()
            }
            TaskCatalogTable.selectAll().where { TaskCatalogTable.id eq id.value }.any()
        }

    private fun ResultRow.toTask() =
        CatalogTask(
            id = CatalogTaskId(this[TaskCatalogTable.id]),
            title = this[TaskCatalogTable.title],
            emoji = this[TaskCatalogTable.emoji],
            points = this[TaskCatalogTable.points],
            archived = this[TaskCatalogTable.archivedAt] != null,
        )
}
