package de.familyplanner.gamification.persistence

import org.jetbrains.exposed.v1.core.Table

object LedgerTable : Table("game_ledger") {
    val seq = long("seq").autoIncrement()
    val id = varchar("id", ID_LENGTH)
    val memberId = varchar("member_id", ID_LENGTH)
    val kind = varchar("kind", KIND_LENGTH)
    val amount = integer("amount")
    val title = varchar("title", TITLE_LENGTH)
    val note = varchar("note", NOTE_LENGTH).nullable()
    val sourceKey = varchar("source_key", SOURCE_KEY_LENGTH).nullable()
    val createdBy = varchar("created_by", ID_LENGTH).nullable()
    val bookedAt = long("booked_at")

    override val primaryKey = PrimaryKey(seq)
}

object GameSettingsTable : Table("game_settings") {
    val id = integer("id")
    val cookingPoints = integer("cooking_points")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

private const val ID_LENGTH = 36
private const val KIND_LENGTH = 13
private const val TITLE_LENGTH = 200
private const val NOTE_LENGTH = 500
private const val SOURCE_KEY_LENGTH = 80
