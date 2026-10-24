package de.familyplanner.tasks.persistence

import org.jetbrains.exposed.v1.core.Table

object TaskCatalogTable : Table("task_catalog") {
    val id = varchar("id", ID_LENGTH)
    val title = varchar("title", TITLE_LENGTH)
    val emoji = varchar("emoji", EMOJI_LENGTH).nullable()
    val points = integer("points")
    val archivedAt = long("archived_at").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object TaskCardsTable : Table("task_cards") {
    val id = varchar("id", ID_LENGTH)
    val memberId = varchar("member_id", ID_LENGTH)
    val catalogTaskId = varchar("catalog_task_id", ID_LENGTH).nullable()
    val title = varchar("title", TITLE_LENGTH)
    val emoji = varchar("emoji", EMOJI_LENGTH).nullable()
    val points = integer("points")
    val status = varchar("status", STATUS_LENGTH)
    val rejectionReason = varchar("rejection_reason", REASON_LENGTH).nullable()
    val assignedAt = long("assigned_at")
    val reportedAt = long("reported_at").nullable()
    val doneAt = long("done_at").nullable()
    val doneBy = varchar("done_by", ID_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

private const val ID_LENGTH = 36
private const val TITLE_LENGTH = 40
private const val EMOJI_LENGTH = 16
private const val STATUS_LENGTH = 10
private const val REASON_LENGTH = 200
